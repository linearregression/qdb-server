package io.qdb.server.model;

import java.io.Closeable;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * Retrieves and persists our model objects. Find methods that return single objects return null if the object
 * does not exist. The returned object is a clone of the one from the store and may be modified. Other find
 * methods do not copy objects and the objects must not be modified.
 *
 * Create and update methods do not clone the incoming object so these objects must not be modified after being
 * created/updated. The repository must accept an EventBust constructor parameter and post events when
 * objects are created or updated (see {@link ModelEvent}).
 *
 * Fires Status instances on the shared EventBus on connect/disconnect events. Use a negative
 * limit parameter for findXXX(offset,limit) methods to fetch all data.
 */
public interface Repository extends Closeable {

    /**
     * Thrown on all repository operations except getStatus if the repo is down.
     */
    public static class UnavailableException extends IOException {
        public UnavailableException(String msg) {
            super(msg);
        }
    }

    public static class Status {
        public Date upSince;
        public boolean isUp() { return upSince != null; }
    }

    public Status getStatus();

    /**
     * Which servers are supposed to be in our cluster?
     */
    public List<Server> findServers() throws IOException;

    public Server createServer(Server server) throws IOException;

    public Server updateServer(Server server) throws IOException;

    public void deleteServer(Server server) throws IOException;


    public User findUser(String id) throws IOException;

    public User createUser(User user) throws IOException;

    public User updateUser(User user) throws IOException;

    public List<User> findUsers(int offset, int limit) throws IOException;

    public int countUsers() throws IOException;


    public Database findDatabase(String id) throws IOException;

    public Database createDatabase(Database db) throws IOException;

    public Database updateDatabase(Database db) throws IOException;

    public List<Database> findDatabasesVisibleTo(User user, int offset, int limit) throws IOException;

    public int countDatabasesVisibleTo(User user) throws IOException;


    public Queue findQueue(String id) throws IOException;

    public Queue createQueue(Queue queue) throws IOException;

    public Queue updateQueue(Queue queue) throws IOException;

    public List<Queue> findQueues(int offset, int limit) throws IOException;

    public int countQueues() throws IOException;

}
