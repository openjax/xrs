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

package org.jetrs.server.container;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.ReaderInterceptor;
import javax.ws.rs.ext.ReaderInterceptorContext;

import org.jetrs.server.ExecutionContext;
import org.jetrs.server.core.DefaultSecurityContext;
import org.jetrs.server.core.UriInfoImpl;
import org.jetrs.server.util.MediaTypes;
import org.openjax.util.Locales;

public class ContainerRequestContextImpl extends InterceptorContextImpl implements ContainerRequestContext, ReaderInterceptorContext {
  private final ReaderInterceptor[] readerInterceptors;
  private final HttpServletRequest httpServletRequest;

  private String method;
  private final HttpHeaders headers;
  private final UriInfo uriInfo;
  private final List<MediaType> accept;
  private final List<Locale> acceptLanguages;
  private InputStream entityStream;

  public ContainerRequestContextImpl(final HttpServletRequest httpServletRequest, final ContainerResponseContextImpl containerResponseContext, final ExecutionContext executionContext, final ReaderInterceptor[] readerInterceptors) {
    super(httpServletRequest.getLocale(), containerResponseContext.properties);
    this.readerInterceptors = readerInterceptors;
    this.method = httpServletRequest.getMethod();
    final Enumeration<String> attributes = httpServletRequest.getAttributeNames();
    for (String attribute; attributes.hasMoreElements();)
      properties.put(attribute = attributes.nextElement(), httpServletRequest.getAttribute(attribute));

    try {
      this.httpServletRequest = httpServletRequest;
      this.accept = Collections.unmodifiableList(Arrays.asList(MediaTypes.parse(httpServletRequest.getHeaders(HttpHeaders.ACCEPT))));
      this.acceptLanguages = Collections.unmodifiableList(Arrays.asList(Locales.parse(httpServletRequest.getHeaders(HttpHeaders.ACCEPT_LANGUAGE))));
      this.headers = executionContext.getRequestHeaders();
      this.uriInfo = new UriInfoImpl(this, httpServletRequest, executionContext);
    }
    catch (final ParseException e) {
      throw new BadRequestException(e);
    }
  }

  @Override
  MultivaluedMap<String,String> getStringHeaders() {
    return headers.getRequestHeaders();
  }

  @Override
  public UriInfo getUriInfo() {
    return uriInfo;
  }

  @Override
  public void setRequestUri(final URI requestUri) {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public void setRequestUri(final URI baseUri, final URI requestUri) {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public Request getRequest() {
    // TODO:
    throw new UnsupportedOperationException();
  }

  @Override
  public String getMethod() {
    return method;
  }

  @Override
  public void setMethod(final String method) {
    this.method = method;
  }

  @Override
  public MultivaluedMap<String,String> getHeaders() {
    return headers.getRequestHeaders();
  }

  @Override
  public List<MediaType> getAcceptableMediaTypes() {
    return accept;
  }

  @Override
  public List<Locale> getAcceptableLanguages() {
    return acceptLanguages;
  }

  @Override
  public Map<String,Cookie> getCookies() {
    return headers.getCookies();
  }

  @Override
  public int getLength() {
    return headers.getLength();
  }

  @Override
  public boolean hasEntity() {
    return headers.getLength() > 0;
  }

  @Override
  public InputStream getEntityStream() {
    if (entityStream != null)
      return entityStream;

    try {
      return httpServletRequest.getInputStream();
    }
    catch (final IOException e) {
      throw new InternalServerErrorException(e);
    }
  }

  @Override
  public void setEntityStream(final InputStream input) {
    this.entityStream = input;
  }

  private SecurityContext defaultSecurityContext;
  private SecurityContext securityContext;

  @Override
  public SecurityContext getSecurityContext() {
    return securityContext != null ? securityContext : defaultSecurityContext == null ? defaultSecurityContext = new DefaultSecurityContext(httpServletRequest) : defaultSecurityContext;
  }

  @Override
  public void setSecurityContext(final SecurityContext context) {
    this.securityContext = context;
  }

  @Override
  public void abortWith(final Response response) {
    // FIXME: throw new IllegalStateException if invoked from response filter
    throw new WebApplicationException(response);
  }

  @Override
  public InputStream getInputStream() {
    return getEntityStream();
  }

  @Override
  public void setInputStream(final InputStream is) {
    this.entityStream = is;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object proceed() throws IOException, WebApplicationException {
    if (readerInterceptors == null || ++interceptorIndex == readerInterceptors.length)
      return lastProceeded = messageBodyReader.readFrom(getType(), getGenericType(), getAnnotations(), getMediaType(), getHeaders(), getInputStream());

    if (interceptorIndex < readerInterceptors.length)
      return lastProceeded = readerInterceptors[interceptorIndex].aroundReadFrom(this);

    return lastProceeded;
  }

  private int interceptorIndex = -1;
  private Object lastProceeded;

  @SuppressWarnings("rawtypes")
  private MessageBodyReader messageBodyReader;

  public Object readBody(final MessageBodyReader<?> messageBodyReader) throws IOException {
    this.messageBodyReader = messageBodyReader;
    return proceed();
  }
}