/* Copyright (c) 2016 OpenJAX
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

package org.jetrs.server.ext;

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

import org.jetrs.server.EntityReaderProviderResource;
import org.jetrs.server.EntityWriterProviderResource;
import org.jetrs.server.ExceptionMappingProviderResource;
import org.jetrs.server.TypeProviderResource;
import org.jetrs.server.core.AnnotationInjector;

public class ProvidersImpl implements Providers {
  public static final Comparator<TypeProviderResource<?>> providerResourceComparator = Comparator.nullsFirst(new Comparator<TypeProviderResource<?>>() {
    @Override
    public int compare(final TypeProviderResource<?> o1, final TypeProviderResource<?> o2) {
      return o1.getType() == o2.getType() ? Integer.compare(o1.getPriority(), o2.getPriority()) : o1.getType().isAssignableFrom(o2.getType()) ? 1 : -1;
    }
  });

  private final List<ExceptionMappingProviderResource> exceptionMappers;
  private final List<EntityReaderProviderResource> entityReaders;
  private final List<EntityWriterProviderResource> entityWriters;
  private final AnnotationInjector annotationInjector;

  public ProvidersImpl(final ProvidersImpl copy, final AnnotationInjector annotationInjector) {
    this.exceptionMappers = copy.exceptionMappers;
    this.entityReaders = copy.entityReaders;
    this.entityWriters = copy.entityWriters;
    this.annotationInjector = annotationInjector;
  }

  public ProvidersImpl(final List<ExceptionMappingProviderResource> exceptionMappers, final List<EntityReaderProviderResource> entityReaders, final List<EntityWriterProviderResource> entityWriters) {
    this.exceptionMappers = exceptionMappers;
    this.entityReaders = entityReaders;
    this.entityWriters = entityWriters;
    this.annotationInjector = null;

    this.exceptionMappers.sort(providerResourceComparator);
    this.entityReaders.sort(providerResourceComparator);
    this.entityWriters.sort(providerResourceComparator);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T>MessageBodyReader<T> getMessageBodyReader(final Class<T> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
    for (final EntityReaderProviderResource provider : entityReaders)
      if (provider.getCompatibleMediaType(provider.getMatchInstance(), type, genericType, annotations, mediaType) != null)
        return (MessageBodyReader<T>)provider.getSingletonOrNewInstance(annotationInjector);

    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T>MessageBodyWriter<T> getMessageBodyWriter(final Class<T> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
    for (final EntityWriterProviderResource provider : entityWriters)
      if (provider.getCompatibleMediaType(provider.getMatchInstance(), type, genericType, annotations, mediaType) != null)
        return (MessageBodyWriter<T>)provider.getSingletonOrNewInstance(annotationInjector);

    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends Throwable>ExceptionMapper<T> getExceptionMapper(final Class<T> type) {
    for (final ExceptionMappingProviderResource exceptionMapper : exceptionMappers)
      if (exceptionMapper.getType().isAssignableFrom(type))
        return (ExceptionMapper<T>)exceptionMapper.getSingletonOrNewInstance(annotationInjector);

    return null;
  }

  @Override
  public <T>ContextResolver<T> getContextResolver(final Class<T> contextType, final MediaType mediaType) {
    throw new UnsupportedOperationException();
  }
}