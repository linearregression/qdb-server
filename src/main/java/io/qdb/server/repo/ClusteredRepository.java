package io.qdb.server.repo;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.io.Closeables;
import io.qdb.server.OurServer;
import io.qdb.server.model.*;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Closeable;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * Repository implementation for clustered deployment. All repository updates are carried out by the leader of the
 * cluster and the transactions replicated to the other nodes. So slave nodes can be a little out of sync but
 * optimistic locking ensures that this does not cause any trouble.
 *
 * Leader election?
 *
 * Cluster membership?
 */
@Singleton
public class ClusteredRepository extends RepositoryBase {

    private final StandaloneRepository local;
    private final JsonConverter jsonConverter;
    private final ServerRegistry serverRegistry;
    private final MasterStrategy masterStrategy;
    private final EventBus eventBus;
    private final OurServer ourServer;
    private final String clusterName;
    private final String clusterPassword;
    private final int masterTimeoutMs;

    private ClusterClient master;
    private Date upSince;

    @Inject
    public ClusteredRepository(StandaloneRepository local, EventBus eventBus, OurServer ourServer,
                ServerRegistry serverRegistry, JsonConverter jsonConverter, MasterStrategy masterStrategy,
                @Named("clusterName") String clusterName,
                @Named("clusterPassword") String clusterPassword,
                @Named("masterTimeoutMs") int masterTimeoutMs) throws IOException {
        this.local = local;
        this.eventBus = eventBus;
        this.serverRegistry = serverRegistry;
        this.jsonConverter = jsonConverter;
        this.masterStrategy = masterStrategy;
        this.masterTimeoutMs = masterTimeoutMs;
        this.ourServer = ourServer;
        this.clusterName = clusterName;
        this.clusterPassword = clusterPassword;

        eventBus.register(this);
        masterStrategy.chooseMaster();
    }

    @Override
    public void close() throws IOException {
        closeQuietly(local);
        closeQuietly(masterStrategy);
        closeQuietly(serverRegistry);
    }

    private void closeQuietly(Closeable o) {
        try {
            o.close();
        } catch (IOException e) {
            log.warn(e.toString(), e);
        }
    }

    @Subscribe
    public void handleMasterFound(MasterStrategy.MasterFound ev) {
        if (log.isDebugEnabled()) log.debug(ev.toString());
        synchronized (this) {
            if (master != null && master.server.equals(ev.master)) return;
            if (master != null) {
                // todo disconnect from old master
            }
            master = new ClusterClient(jsonConverter, ev.master, clusterName, clusterPassword, masterTimeoutMs);
        }
    }

    private synchronized boolean isMaster() {
        return master != null && ourServer.equals(master.server);
    }

    private synchronized void chooseMaster() {
        master = null;
        upSince = null;
        masterStrategy.chooseMaster();
    }

    /**
     * Append tx to our tx log and apply it to our in memory model. This is used to append transactions from slaves.
     * Throws an exception if we are not the master. Returns the transaction id to be sent back to the client.
     */
    public synchronized long appendTxFromSlave(RepoTx tx) throws IOException, ModelException {
        if (!isMaster()) throw new ClusterException.NotMaster();
        return local.exec(tx);
    }

    @Override
    protected long exec(RepoTx tx) throws IOException, ModelException {
        synchronized (this) {
            if (upSince == null) throw new UnavailableException("Not available"); // todo more detailed status
            if (isMaster()) return local.exec(tx);
        }

        StandaloneRepository.TxMonitor txMonitor = local.createTxMonitor();
        try {
            long id;
            try {
                id = master.POST("cluster/transactions", tx, TxId.class).id;
            } catch (ResponseCodeException e) {
                if (e.responseCode == 409) throw new ModelException(e.text);
                log.error(e.getMessage());
                if (e.responseCode == 410) chooseMaster();
                throw e;
            }
            if (txMonitor.waitFor(id, masterTimeoutMs)) return id;
            String msg = "Timeout waiting for tx " + id + " from master " + master;
            log.error(msg);
            chooseMaster();
            throw new ClusterException.MasterTimeout(msg);
        } finally {
            try {
                txMonitor.close();
            } catch (IOException e) {
                log.warn("Error closing txMonitor: " + e);
            }
        }
    }

    @Override
    public Status getStatus() {
        Status s = new Status();
        s.upSince = upSince;
        return s;
    }

    @Override
    public List<Server> findServers() throws IOException {
        return local.findServers();
    }

    @Override
    public User findUser(String id) throws IOException {
        return local.findUser(id);
    }

    @Override
    public List<User> findUsers(int offset, int limit) throws IOException {
        return local.findUsers(offset, limit);
    }

    @Override
    public int countUsers() throws IOException {
        return local.countUsers();
    }

    @Override
    public Database findDatabase(String id) throws IOException {
        return local.findDatabase(id);
    }

    @Override
    public List<Database> findDatabasesVisibleTo(User user, int offset, int limit) throws IOException {
        return local.findDatabasesVisibleTo(user, offset, limit);
    }

    @Override
    public int countDatabasesVisibleTo(User user) throws IOException {
        return local.countDatabasesVisibleTo(user);
    }

    @Override
    public Queue findQueue(String id) throws IOException {
        return local.findQueue(id);
    }

    @Override
    public List<Queue> findQueues(int offset, int limit) throws IOException {
        return local.findQueues(offset, limit);
    }

    @Override
    public int countQueues() throws IOException {
        return local.countQueues();
    }
}
