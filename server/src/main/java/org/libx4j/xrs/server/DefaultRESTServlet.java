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

package org.libx4j.xrs.server;

import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.RuntimeDelegate;

import org.lib4j.lang.Classes;
import org.lib4j.lang.Throwables;
import org.libx4j.xrs.server.container.ContainerRequestContextImpl;
import org.libx4j.xrs.server.container.ContainerResponseContextImpl;
import org.libx4j.xrs.server.core.ContextInjector;
import org.libx4j.xrs.server.core.HttpHeadersImpl;
import org.libx4j.xrs.server.core.RequestImpl;
import org.libx4j.xrs.server.ext.RuntimeDelegateImpl;
import org.slf4j.Logger;

@WebServlet(name="javax.ws.rs.core.Application", urlPatterns="/*")
public abstract class DefaultRESTServlet extends StartupServlet {
  private static final long serialVersionUID = 3700080355780006441L;

  static {
    System.setProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY, RuntimeDelegateImpl.class.getName());
  }

  private static String getApplicationClassName(final WebServlet webServlet) {
    final WebInitParam[] webInitParams = webServlet.initParams();
    if (webInitParams != null)
      for (final WebInitParam webInitParam : webInitParams)
        if ("javax.ws.rs.Application".equals(webInitParam.name()))
          return webInitParam.value();

    return null;
  }

  public DefaultRESTServlet() {
    final WebServlet webServlet = getClass().getAnnotation(WebServlet.class);
    if (webServlet == null)
      return;

    final String applicationClassName = getApplicationClassName(webServlet);
    if (applicationClassName == null)
      return;

    Classes.setAnnotationValue(DefaultRESTServlet.class.getAnnotation(WebServlet.class), "urlPatterns", new String[0]);
    Classes.setAnnotationValue(webServlet, "name", applicationClassName);
    if (webServlet.urlPatterns().length > 0 || webServlet.value().length > 0)
      return;

    try {
      final Class<?> applicationClass = Class.forName(applicationClassName);
      final ApplicationPath applicationPath = applicationClass.getAnnotation(ApplicationPath.class);
      if (applicationPath != null)
        Classes.setAnnotationValue(webServlet, "urlPatterns", new String[] {applicationPath.value()});
    }
    catch (final ClassNotFoundException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private void service(final HttpServletRequestContext request, final HttpServletResponse response) throws IOException, ServletException {
    ExecutionContext executionContext = null;
    WebApplicationException executionException = null;

    try {
      final ContainerResponseContext containerResponseContext = new ContainerResponseContextImpl(response);
      final HttpHeaders httpHeaders = new HttpHeadersImpl(request);
      executionContext = new ExecutionContext(httpHeaders, response, containerResponseContext, getResourceContext());

      final ContainerRequestContext containerRequestContext; // NOTE: This weird construct is done this way to at least somehow make the two objects cohesive
      request.setRequestContext(containerRequestContext = new ContainerRequestContextImpl(request, executionContext));

      final ContextInjector injectionContext = ContextInjector.createInjectionContext(containerRequestContext, new RequestImpl(request.getMethod()), httpHeaders, getResourceContext().getProviders());

      try {
        getResourceContext().getContainerFilters().filterPreMatchContainerRequest(containerRequestContext, injectionContext);
        final ResourceMatch resource = executionContext.filterAndMatch(containerRequestContext);
        if (resource == null)
          throw new NotFoundException();

        request.setResourceManifest(resource.getManifest());
        getResourceContext().getContainerFilters().filterContainerRequest(containerRequestContext, injectionContext);

        final Produces produces = resource.getManifest().getMatcher(Produces.class).getAnnotation();
        if (produces != null)
          containerResponseContext.getStringHeaders().putSingle(HttpHeaders.CONTENT_TYPE, resource.getAccept().toString());

        final Object content = resource.getManifest().service(executionContext, containerRequestContext, injectionContext, getResourceContext().getParamConverterProviders());
        if (content instanceof Response)
          executionContext.setResponse((Response)content);
        else if (content != null)
          containerResponseContext.setEntity(content);

        getResourceContext().getContainerFilters().filterContainerResponse(containerRequestContext, containerResponseContext, injectionContext);
      }
      catch (final WebApplicationException e) {
        executionException = e;
        executionContext.setResponse(e.getResponse());
        getResourceContext().getContainerFilters().filterContainerResponse(containerRequestContext, containerResponseContext, injectionContext);
        if (e.getResponse().getStatus() == 200)
          getLogger().info(e.getMessage(), e);
        else
          getLogger().error(e.getMessage(), e);
      }
    }
    catch (final IOException | RuntimeException | ServletException e) {
      if (executionException != null)
        Throwables.set(e, executionException);

      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // Or should this use the formal InternalServerErrorException?
      throw e;
    }
    finally {
      if (executionContext != null)
        executionContext.commit(getResourceContext().getProviders());
    }
  }

  @Override
  protected final void service(final HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
    try {
      service(new HttpServletRequestContext(request) {
        // NOTE: Check for the existence of the @Consumes header, and subsequently the Content-Type header in the request,
        // NOTE: only if data is expected (i.e. GET, HEAD, DELETE, OPTIONS methods will not have a body and should thus not
        // NOTE: expect a Content-Type header from the request)
        private void checkContentType() {
          if (getResourceManifest() != null && !getResourceManifest().checkHeader(HttpHeaders.CONTENT_TYPE, Consumes.class, getRequestContext()))
            throw new BadRequestException("Request has data yet missing Content-Type header");
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
          checkContentType();
          return request.getInputStream();
        }

        @Override
        public BufferedReader getReader() throws IOException {
          checkContentType();
          return request.getReader();
        }
      }, response);
    }
    catch (final IOException | RuntimeException | ServletException e) {
      if (e.getCause() instanceof ServletException)
        throw (ServletException)e.getCause();

      throw e;
    }
  }

  protected abstract Logger getLogger();
}