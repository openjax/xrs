/* Copyright (c) 2016 lib4j
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * You should have received a copy of The MIT License (MIT) along with this
 * program. If not, see <http://opensource.org/licenses/MIT/>.
 */

package org.libx4j.xrs.server.ext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Providers;

import org.lib4j.util.Collections;
import org.libx4j.xrs.server.EntityProviderResource;
import org.libx4j.xrs.server.ExceptionMappingProviderResource;
import org.libx4j.xrs.server.EntityReaderProviderResource;
import org.libx4j.xrs.server.EntityWriterProviderResource;

public class ProvidersImpl implements Providers {
  private static final Comparator<ExceptionMappingProviderResource> exceptionMapperComparator = new Comparator<ExceptionMappingProviderResource>() {
    @Override
    public int compare(final ExceptionMappingProviderResource o1, final ExceptionMappingProviderResource o2) {
      return o1.getExceptionType() == o2.getExceptionType() ? 0 : o1.getExceptionType().isAssignableFrom(o2.getExceptionType()) ? 1 : -1;
    }
  };

  private static final Comparator<EntityProviderResource<?>> messageBodyComparator = new Comparator<EntityProviderResource<?>>() {
    @Override
    public int compare(final EntityProviderResource<?> o1, final EntityProviderResource<?> o2) {
      return o1.getType() == o2.getType() ? 0 : o1.getType().isAssignableFrom(o2.getType()) ? 1 : -1;
    }
  };

  private final List<ExceptionMappingProviderResource> exceptionMappers;
  private final List<EntityReaderProviderResource> readerProviders;
  private final List<EntityWriterProviderResource> writerProviders;

  public ProvidersImpl(final List<ExceptionMappingProviderResource> exceptionMappers, final List<EntityReaderProviderResource> readerProviders, final List<EntityWriterProviderResource> writerProviders) {
    this.exceptionMappers = exceptionMappers;
    this.readerProviders = readerProviders;
    this.writerProviders = writerProviders;

    Collections.sort(this.exceptionMappers, exceptionMapperComparator);
    Collections.sort(this.readerProviders, messageBodyComparator);
    Collections.sort(this.writerProviders, messageBodyComparator);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T>MessageBodyReader<T> getMessageBodyReader(final Class<T> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
    for (final EntityReaderProviderResource provider : readerProviders)
      if (provider.matches(provider.getMatchInstance(), type, genericType, annotations, mediaType))
        return (MessageBodyReader<T>)provider.getSingletonOrNewInstance();

    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T>MessageBodyWriter<T> getMessageBodyWriter(final Class<T> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
    for (final EntityWriterProviderResource provider : writerProviders)
      if (provider.matches(provider.getMatchInstance(), type, genericType, annotations, mediaType))
        return (MessageBodyWriter<T>)provider.getSingletonOrNewInstance();

    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends Throwable>ExceptionMapper<T> getExceptionMapper(final Class<T> type) {
    for (final ExceptionMappingProviderResource exceptionMapper : exceptionMappers)
      if (exceptionMapper.getExceptionType().isAssignableFrom(type))
        return (ExceptionMapper<T>)exceptionMapper.getSingletonOrNewInstance();

    return null;
  }

  @Override
  public <T>ContextResolver<T> getContextResolver(final Class<T> contextType, final MediaType mediaType) {
    return null;
  }
}