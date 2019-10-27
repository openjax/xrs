/* Copyright (c) 2016 JetRS
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

package org.jetrs.common.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Link.Builder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Providers;
import javax.ws.rs.ext.ReaderInterceptor;

import org.jetrs.common.ext.ReaderInterceptorContextImpl;
import org.libj.io.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseImpl extends Response {
  private static final Logger logger = LoggerFactory.getLogger(ResponseImpl.class);

  private final Providers providers;
  private final Object[] readerInterceptors;
  private final Response.StatusType status;
  private final HttpHeadersImpl headers;
  private final Map<String,NewCookie> cookies;
  private Object entity;
  private boolean closed;

  // FIXME: annotations are not being used.. there's no API to get them out of this class
  public ResponseImpl(final Providers providers, final Object[] readerInterceptors, final Response.StatusType status, final HttpHeadersImpl headers, final Map<String,NewCookie> cookies, final Object entity, final Annotation[] annotations) {
    this.providers = providers;
    this.readerInterceptors = readerInterceptors;
    this.status = status;
    this.headers = headers;
    this.cookies = cookies;
    this.entity = entity;
  }

  @Override
  public int getStatus() {
    return status.getStatusCode();
  }

  @Override
  public StatusType getStatusInfo() {
    return status;
  }

  @Override
  public Object getEntity() {
    if (entity instanceof InputStream && closed)
      throw new IllegalStateException("response has been closed");

    return entity;
  }

  @Override
  public <T>T readEntity(final Class<T> entityType) {
    return readEntity(entityType, null);
  }

  @Override
  public <T>T readEntity(final GenericType<T> entityType) {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public <T>T readEntity(final Class<T> entityType, final Annotation[] annotations) {
    if (!(entity instanceof InputStream))
      throw new IllegalStateException("Entity is not an instance of InputStream");

    final MessageBodyReader<T> messageBodyReader = providers.getMessageBodyReader(entityType, entityType, annotations, null);
    if (messageBodyReader == null)
      throw new ProcessingException("Could not find MessageBodyReader for type: " + entityType.getName());

    if (closed && entityBuffer == null)
      throw new IllegalStateException("Entity InputStream was previously consumed and not buffered");

    try {
      final InputStream in = closed ? new ByteArrayInputStream(entityBuffer) : (InputStream)entity;
      if (readerInterceptors == null)
        return (T)(entity = messageBodyReader.readFrom(entityType, entityType, annotations, headers.getMediaType(), headers, in));

      final ReaderInterceptorContextImpl readerInterceptorContext = new ReaderInterceptorContextImpl(entityType, entityType, annotations, headers, in) {
        private int interceptorIndex = -1;
        private Object lastProceeded;

        @Override
        public Object proceed() throws IOException {
          if (readerInterceptors == null || ++interceptorIndex == readerInterceptors.length)
            return lastProceeded = ((MessageBodyReader)messageBodyReader).readFrom(getType(), getGenericType(), getAnnotations(), getMediaType(), getHeaders(), getInputStream());

          if (interceptorIndex < readerInterceptors.length)
            return lastProceeded = ((ReaderInterceptor)readerInterceptors[interceptorIndex]).aroundReadFrom(this);

          return lastProceeded;
        }
      };

      return (T)(entity = readerInterceptorContext.proceed());
    }
    catch (final IOException e) {
      throw new IllegalStateException(e);
    }
    finally {
      close();
    }
  }

  @Override
  public <T>T readEntity(final GenericType<T> entityType, final Annotation[] annotations) {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasEntity() {
    return entity != null;
  }

  private boolean buffered = false;
  private byte[] entityBuffer;

  @Override
  public boolean bufferEntity() {
    if (buffered)
      return true;

    if (entity instanceof InputStream) {
      final InputStream in = (InputStream)entity;
      try {
        entityBuffer = Streams.readBytes(in);
      }
      catch (final IOException e) {
        return false;
      }

      close();
    }

    return buffered = true;
  }

  @Override
  public void close() {
    if (entity instanceof InputStream) {
      this.closed = true;
      try {
        ((InputStream)entity).close();
      }
      catch (final IOException e) {
        logger.warn("Error closing response", e);
      }
    }
  }

  @Override
  public MediaType getMediaType() {
    return (MediaType)getMetadata().getFirst(HttpHeaders.CONTENT_TYPE);
  }

  @Override
  public Locale getLanguage() {
    return headers.getLanguage();
  }

  @Override
  public int getLength() {
    return headers.getLength();
  }

  @Override
  public Set<String> getAllowedMethods() {
    return headers.getAllowedMethods();
  }

  @Override
  public Map<String,NewCookie> getCookies() {
    return cookies;
  }

  @Override
  public EntityTag getEntityTag() {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public Date getDate() {
    return headers.getDate();
  }

  @Override
  public Date getLastModified() {
    return headers.getLastModified();
  }

  @Override
  public URI getLocation() {
    return headers.getLocation();
  }

  @Override
  public Set<Link> getLinks() {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasLink(final String relation) {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public Link getLink(final String relation) {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public Builder getLinkBuilder(final String relation) {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public MultivaluedMap<String,Object> getMetadata() {
    return headers.getMirror();
  }

  @Override
  public MultivaluedMap<String,String> getStringHeaders() {
    return headers;
  }

  @Override
  public String getHeaderString(final String name) {
    return headers.getString(name);
  }
}