/* Copyright (c) 2019 JetRS
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

package org.jetrs.client;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Providers;

import org.jetrs.common.Bootstrap;
import org.jetrs.common.EntityReaderProviderResource;
import org.jetrs.common.EntityWriterProviderResource;
import org.jetrs.common.ExceptionMappingProviderResource;
import org.jetrs.common.ProviderResource;
import org.jetrs.common.ReaderInterceptorEntityProviderResource;
import org.jetrs.common.WriterInterceptorEntityProviderResource;
import org.jetrs.common.ext.ProvidersImpl;
import org.libj.lang.PackageNotFoundException;

public class ClientImpl implements Client, ClientConfigurable<Client> {
  private final Configuration config;
  private final SSLContext sslContext;
  private final HostnameVerifier verifier;
  private final ExecutorService executorService;
  private final ScheduledExecutorService scheduledExecutorService;
  private final long connectTimeout;
  private final TimeUnit connectUnit;
  private final long readTimeout;
  private final TimeUnit readUnit;

  public ClientImpl(final Configuration config, final SSLContext sslContext, final HostnameVerifier verifier, final ExecutorService executorService, final ScheduledExecutorService scheduledExecutorService, final long connectTimeout, final TimeUnit connectUnit, final long readTimeout, final TimeUnit readUnit) {
    this.config = config;
    this.sslContext = sslContext;
    this.verifier = verifier;
    this.executorService = executorService;
    this.scheduledExecutorService = scheduledExecutorService;
    this.connectTimeout = connectTimeout;
    this.connectUnit = connectUnit;
    this.readTimeout = readTimeout;
    this.readUnit = readUnit;
  }

  // TODO: This should only rebuild providers if config has changed!
  private Providers buildProviders() {
    try {
      final List<ExceptionMappingProviderResource> exceptionMappers = new ArrayList<>();
      final List<EntityReaderProviderResource> entityReaders = new ArrayList<>();
      final List<EntityWriterProviderResource> entityWriters = new ArrayList<>();
      final List<ProviderResource<ContainerRequestFilter>> requestFilters = new ArrayList<>();
      final List<ProviderResource<ContainerResponseFilter>> responseFilters = new ArrayList<>();
      final List<ReaderInterceptorEntityProviderResource> readerInterceptors = new ArrayList<>();
      final List<WriterInterceptorEntityProviderResource> writerInterceptors = new ArrayList<>();
      final List<ProviderResource<ParamConverterProvider>> paramConverterProviders = new ArrayList<>();

      final Bootstrap<Void> bootstrap = new Bootstrap<>();
      bootstrap.init(config.getInstances(), config.getClasses(), null, exceptionMappers, entityReaders, entityWriters, requestFilters, responseFilters, readerInterceptors, writerInterceptors, paramConverterProviders);
      return new ProvidersImpl(exceptionMappers, entityReaders, entityWriters);
    }
    catch (final IllegalAccessException | InstantiationException | InvocationTargetException | PackageNotFoundException | IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Configuration getConfiguration() {
    return config;
  }

  @Override
  public SSLContext getSslContext() {
    return sslContext;
  }

  @Override
  public HostnameVerifier getHostnameVerifier() {
    return verifier;
  }

  @Override
  public WebTarget target(final String uri) {
    return new WebTargetImpl(buildProviders(), config, UriBuilder.fromUri(uri));
  }

  @Override
  public WebTarget target(final URI uri) {
    return new WebTargetImpl(buildProviders(), config, UriBuilder.fromUri(uri));
  }

  @Override
  public WebTarget target(final UriBuilder uriBuilder) {
    return new WebTargetImpl(buildProviders(), config, uriBuilder);
  }

  @Override
  public WebTarget target(final Link link) {
    return new WebTargetImpl(buildProviders(), config, UriBuilder.fromLink(link));
  }

  @Override
  public Invocation.Builder invocation(final Link link) {
    try {
      return new InvocationImpl.BuilderImpl(buildProviders(), link.getUri().toURL());
    }
    catch (final MalformedURLException e) {
      throw new ProcessingException(e);
    }
  }

  @Override
  public void close() {
    // TODO
  }
}