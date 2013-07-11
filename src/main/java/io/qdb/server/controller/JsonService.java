/*
 * Copyright 2013 David Tinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.qdb.server.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import com.google.inject.Inject;
import io.qdb.server.databind.DateTimeParser;
import io.qdb.server.databind.IntegerParser;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Marshaling of objects to/from JSON using Jackson.
 */
@Singleton
public class JsonService {

    private final ObjectMapper mapper;
    private final ObjectMapper mapperNoIdentOutput;
    private final ObjectMapper mapperBorg;

    @Inject
    @SuppressWarnings("deprecation")
    public JsonService(@Named("prettyPrint") boolean prettyPrint) {
        SimpleModule qdbModule = createQdbModule();

        mapperNoIdentOutput = createMapper(qdbModule, false);
        mapper = createMapper(qdbModule, prettyPrint);
//        mapper.registerModule(createHumanModule());

        mapperBorg = createMapper(qdbModule, prettyPrint);
    }

    private ObjectMapper createMapper(Module module, boolean indent) {
        ObjectMapper m = new ObjectMapper();
        m.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        m.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        m.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        if (indent) m.configure(SerializationFeature.INDENT_OUTPUT, true);
        m.registerModule(module);
        return m;
    }

    private SimpleModule createHumanModule() {
        SimpleModule module = new SimpleModule("qdb-human");
        module.addSerializer(Long.TYPE, new StdScalarSerializer<Long>(Long.TYPE) {
            @Override
            public void serialize(Long value, JsonGenerator jgen, SerializerProvider provider)
                    throws IOException, JsonProcessingException {
                jgen.writeString("x" + value + "x");
            }
        });
        return module;
    }

    private SimpleModule createQdbModule() {
        SimpleModule module = new SimpleModule("qdb");

        module.addSerializer(Date.class, new JsonSerializer<Date>(){
            private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ"); // ISO 8601
            @Override
            public void serialize(Date value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
                jgen.writeString(df.format(value));
            }
        });

        module.addDeserializer(Date.class, new JsonDeserializer<Date>() {
            @Override
            public Date deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
                if (jp.getCurrentToken() == JsonToken.VALUE_NUMBER_INT) return new Date(jp.getLongValue());
                String s = jp.getText().trim();
                try {
                    return DateTimeParser.INSTANCE.parse(s);
                } catch (ParseException e) {
                    throw new IllegalArgumentException("Invalid date: [" + s + "]");
                }
            }
        });

        JsonDeserializer<Integer> integerJsonDeserializer = new JsonDeserializer<Integer>() {
            @Override
            public Integer deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
                if (jp.getCurrentToken() == JsonToken.VALUE_NUMBER_INT) return jp.getIntValue();
                return IntegerParser.INSTANCE.parseInt(jp.getText().trim());
            }
        };
        module.addDeserializer(Integer.class, integerJsonDeserializer);
        module.addDeserializer(Integer.TYPE, integerJsonDeserializer);

        JsonDeserializer<Long> longJsonDeserializer = new JsonDeserializer<Long>() {
            @Override
            public Long deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
                if (jp.getCurrentToken() == JsonToken.VALUE_NUMBER_INT) return jp.getLongValue();
                return IntegerParser.INSTANCE.parseLong(jp.getText().trim());
            }
        };
        module.addDeserializer(Long.class, longJsonDeserializer);
        module.addDeserializer(Long.TYPE, longJsonDeserializer);
        return module;
    }

    /**
     * Convert o to JSON.
     */
    public byte[] toJson(Object o, boolean borg) throws IOException {
        System.out.println("toJson borg=" + borg);
        return borg ? mapperBorg.writeValueAsBytes(o) : mapper.writeValueAsBytes(o);
    }

    /**
     * Convert o to JSON with no indenting.
     */
    public byte[] toJsonNoIndenting(Object o) throws IOException {
        return mapperNoIdentOutput.writeValueAsBytes(o);
    }

    /**
     * Converts content to an instance of a particular type. Throws IllegalArgumentException if JSON is invalid.
     */
    public <T> T fromJson(InputStream ins, Class<T> klass) throws IOException {
        try {
            return mapper.readValue(ins, klass);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }
}
