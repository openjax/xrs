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

package org.jetrs.common.core;

import static org.jetrs.common.core.UriBuilderUtil.*;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.Path;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;

import org.jetrs.common.core.UriBuilderUtil.TemplateParametersMap;

public class UriBuilderImpl extends UriBuilder implements Cloneable {
  static final Pattern opaqueUri = Pattern.compile("^([^:/?#{]+):([^/].*)");
  private static final Pattern hierarchicalUri = Pattern.compile("^(([^:/?#{]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");
  private static final Pattern squareHostBrackets = Pattern.compile("(\\[(([0-9A-Fa-f]{0,4}:){2,7})([0-9A-Fa-f]{0,4})%?.*\\]):(\\d+)");
  private static final Pattern hostPortPattern = Pattern.compile("([^/:]+):(\\d+)");

  private UriBuilder parseHierarchicalUri(final CharSequence uriTemplate, final Matcher match) {
    final boolean hasScheme = match.group(2) != null;
    if (hasScheme)
      this.scheme = match.group(2);

    final String authority = match.group(4);
    if (authority != null) {
      this.authority = null;
      String host = match.group(4);
      final int at = host.indexOf('@');
      if (at > -1) {
        final String user = host.substring(0, at);
        host = host.substring(at + 1);
        this.userInfo = user;
      }

      try {
        final Matcher hostPortMatch = hostPortPattern.matcher(host);
        if (hostPortMatch.matches()) {
          this.host = hostPortMatch.group(1);
          this.port = Integer.parseInt(hostPortMatch.group(2));
        }
        else {
          if (host.startsWith("[")) {
            // Must support an IPv6 hostname of format "[::1]" or
            // [0:0:0:0:0:0:0:0]
            // and IPv6 link-local format [fe80::1234%1]
            // [ff08::9abc%interface10]
            final Matcher bracketsMatch = squareHostBrackets.matcher(host);
            if (bracketsMatch.matches()) {
              host = bracketsMatch.group(1);
              this.port = Integer.parseInt(bracketsMatch.group(5));
            }
          }

          this.host = host;
        }
      }
      catch (final NumberFormatException e) {
        throw new IllegalArgumentException(invalid("uri template", uriTemplate), e);
      }
    }

    if (match.group(5) != null) {
      final String group = match.group(5);
      if (!hasScheme && !group.isEmpty() && !group.startsWith("/") && group.indexOf(':') > -1 && group.indexOf('/') > -1 && group.indexOf(':') < group.indexOf('/'))
        throw new IllegalArgumentException(invalid("uri template", uriTemplate));

      if (!group.isEmpty())
        replacePath(group);
    }

    if (match.group(7) != null)
      replaceQuery(match.group(7));

    if (match.group(9) != null)
      fragment(match.group(9));

    return this;
  }

  private UriBuilder uri(final CharSequence uriTemplate) {
    if (uriTemplate == null)
      throw new IllegalArgumentException(invalidParam("uriTemplate", null));

    final Matcher opaque = opaqueUri.matcher(uriTemplate);
    if (opaque.matches()) {
      this.authority = null;
      this.host = null;
      this.port = -1;
      this.userInfo = null;
      this.query = null;
      this.scheme = opaque.group(1);
      this.ssp = opaque.group(2);
      return this;
    }

    final Matcher matcher = hierarchicalUri.matcher(uriTemplate);
    if (!matcher.matches())
      throw new IllegalArgumentException(invalid("uri template", uriTemplate));

    ssp = null;
    return parseHierarchicalUri(uriTemplate, matcher);
  }

  private StringBuilder buildFromParameters(final Map<String,?> parameters, final boolean fromEncodedMap, final boolean isTemplate, final boolean encodeSlash) {
    final UriEncoder uriEncoder = encodeSlash ? UriEncoder.PATH_SEGMENT : UriEncoder.PATH;
    final StringBuilder builder = new StringBuilder();
    if (scheme != null) {
      replaceParameter(parameters, fromEncodedMap, isTemplate, scheme, builder, uriEncoder);
      builder.append(':');
    }

    if (ssp != null) {
      builder.append(ssp);
    }
    else if (userInfo != null || host != null || port != -1) {
      builder.append("//");
      if (userInfo != null) {
        replaceParameter(parameters, fromEncodedMap, isTemplate, userInfo, builder, uriEncoder);
        builder.append('@');
      }

      if (host != null) {
        if (host.isEmpty())
          throw new UriBuilderException(invalid("host", "\"\""));

        replaceParameter(parameters, fromEncodedMap, isTemplate, host, builder, uriEncoder);
      }

      if (port != -1)
        builder.append(':').append(port);
    }
    else if (authority != null) {
      builder.append("//");
      replaceParameter(parameters, fromEncodedMap, isTemplate, authority, builder, uriEncoder);
    }

    if (path != null) {
      final StringBuilder pathBuilder = new StringBuilder();
      replaceParameter(parameters, fromEncodedMap, isTemplate, path, pathBuilder, uriEncoder);
      if ((userInfo != null || host != null) && pathBuilder.length() > 0 && pathBuilder.charAt(0) != '/')
        builder.append('/');

      builder.append(pathBuilder);
    }

    if (query != null) {
      builder.append('?');
      replaceParameter(parameters, fromEncodedMap, isTemplate, query, builder, UriEncoder.QUERY_PARAM);
    }

    if (fragment != null) {
      builder.append('#');
      replaceParameter(parameters, fromEncodedMap, isTemplate, fragment, builder, uriEncoder);
    }

    return builder;
  }

  private String host;
  private String scheme;
  private int port = -1;

  private String userInfo;
  private String path;
  private String query;
  private String fragment;
  private String ssp;
  private String authority;

  @Override
  public UriBuilder uri(final String uriTemplate) throws IllegalArgumentException {
    return uri((CharSequence)uriTemplate);
  }

  @Override
  public UriBuilder uri(final URI uri) throws IllegalArgumentException {
    if (uri == null)
      throw new IllegalArgumentException(invalidParam("uri", null));

    if (uri.getRawFragment() != null)
      fragment = uri.getRawFragment();

    if (uri.isOpaque()) {
      scheme = uri.getScheme();
      ssp = uri.getRawSchemeSpecificPart();
      return this;
    }

    if (uri.getScheme() != null) {
      scheme = uri.getScheme();
    }
    else if (ssp != null && uri.getRawSchemeSpecificPart() != null) {
      ssp = uri.getRawSchemeSpecificPart();
      return this;
    }

    ssp = null;
    if (uri.getRawAuthority() != null) {
      if (uri.getRawUserInfo() == null && uri.getHost() == null && uri.getPort() == -1) {
        authority = uri.getRawAuthority();
        userInfo = null;
        host = null;
        port = -1;
      }
      else {
        authority = null;
        if (uri.getRawUserInfo() != null)
          userInfo = uri.getRawUserInfo();

        if (uri.getHost() != null)
          host = uri.getHost();

        if (uri.getPort() != -1)
          port = uri.getPort();
      }
    }

    if (uri.getRawPath() != null && uri.getRawPath().length() > 0)
      path = uri.getRawPath();

    if (uri.getRawQuery() != null && uri.getRawQuery().length() > 0)
      query = uri.getRawQuery();

    return this;
  }

  @Override
  public UriBuilder scheme(final String scheme) throws IllegalArgumentException {
    this.scheme = scheme;
    return this;
  }

  @Override
  public UriBuilder schemeSpecificPart(final String ssp) throws IllegalArgumentException {
    if (ssp == null)
      throw new IllegalArgumentException(invalidParam("ssp", null));

    final StringBuilder builder = new StringBuilder();
    if (scheme != null)
      builder.append(scheme).append(':');

    builder.append(ssp);
    if (fragment != null && fragment.length() > 0)
      builder.append('#').append(fragment);

    final URI uri = newURI(builder.toString());
    if (uri.getRawSchemeSpecificPart() != null && uri.getRawPath() == null) {
      this.ssp = uri.getRawSchemeSpecificPart();
    }
    else {
      this.ssp = null;
      userInfo = uri.getRawUserInfo();
      host = uri.getHost();
      port = uri.getPort();
      path = uri.getRawPath();
      query = uri.getRawQuery();
    }

    return this;
  }

  @Override
  public UriBuilder userInfo(final String ui) {
    this.userInfo = ui;
    return this;
  }

  @Override
  public UriBuilder host(final String host) throws IllegalArgumentException {
    if (host != null && host.isEmpty())
      throw new IllegalArgumentException(invalidParam("host", "\"" + host + "\""));

    this.host = host;
    return this;
  }

  @Override
  public UriBuilder port(final int port) throws IllegalArgumentException {
    if (port < -1)
      throw new IllegalArgumentException(invalidParam("port", port));

    this.port = port;
    return this;
  }

  @Override
  public UriBuilder path(final String segment) throws IllegalArgumentException {
    if (segment == null)
      throw new IllegalArgumentException(invalidParam("segment", null));

    path = appendPath(path, true, segment);
    return this;
  }

  @Override
  public UriBuilder path(final Class resource) throws IllegalArgumentException {
    if (resource == null)
      throw new IllegalArgumentException(invalidParam("resource", null));

    final Path annotation = (Path)resource.getAnnotation(Path.class);
    if (annotation == null)
      throw new IllegalArgumentException("Path resource not annotated with @Path: " + resource.getName());

    path = appendPath(path, true, annotation.value());
    return this;
  }

  @Override
  public UriBuilder path(final Class resource, final String method) throws IllegalArgumentException {
    if (resource == null)
      throw new IllegalArgumentException(invalidParam("resource", null));

    if (method == null)
      throw new IllegalArgumentException(invalidParam("method", null));

    Method theMethod = null;
    for (final Method m : resource.getMethods()) {
      if (m.getName().equals(method) && m.isAnnotationPresent(Path.class)) {
        if (theMethod != null)
          throw new IllegalArgumentException("Multiple public @Path annotated methods with name \"" + method + "\"");

        theMethod = m;
      }
    }

    if (theMethod == null)
      throw new IllegalArgumentException("No public @Path annotated method for " + resource.getName() + "." + method);

    return path(theMethod);
  }

  @Override
  public UriBuilder path(final Method method) throws IllegalArgumentException {
    if (method == null)
      throw new IllegalArgumentException(invalidParam("method", null));

    final Path annotation = method.getAnnotation(Path.class);
    if (annotation == null)
      throw new IllegalArgumentException("Method \"" + method.getName() + "\" is not annotated with @Path");

    path = appendPath(path, true, annotation.value());
    return this;
  }

  @Override
  public UriBuilder replaceMatrix(String matrix) throws IllegalArgumentException {
    if (matrix == null)
      matrix = ";";
    else if (!matrix.startsWith(";"))
      matrix = ";" + matrix;

    matrix = UriEncoder.PATH.encode(matrix);
    if (path == null) {
      path = matrix;
    }
    else {
      int start = path.lastIndexOf('/');
      if (start < 0)
        start = 0;

      final int matrixIndex = path.indexOf(';', start);
      if (matrixIndex > -1)
        path = path.substring(0, matrixIndex);

      path += matrix;
    }

    return this;
  }

  @Override
  public UriBuilder replaceQuery(final String query) throws IllegalArgumentException {
    this.query = query == null || query.length() == 0 ? null : UriEncoder.QUERY.encode(query);
    return this;
  }

  @Override
  public UriBuilder fragment(final String fragment) throws IllegalArgumentException {
    this.fragment = fragment == null ? null : UriEncoder.QUERY.encode(fragment);
    return this;
  }

  @Override
  public URI build(final Object ... values) throws IllegalArgumentException, UriBuilderException {
    return build(values, true);
  }

  @Override
  public URI build(final Object[] values, final boolean encodeSlashInPath) throws IllegalArgumentException, UriBuilderException {
    if (values == null)
      throw new IllegalArgumentException(invalidParam("values", null));

    return newURI(buildFromParameters(new TemplateParametersMap(values), false, false, encodeSlashInPath).toString());
  }

  @Override
  public URI buildFromMap(final Map<String,?> values) throws IllegalArgumentException, UriBuilderException {
    return buildFromMap(values, true);
  }

  @Override
  public URI buildFromMap(final Map<String,?> values, final boolean encodeSlashInPath) throws IllegalArgumentException, UriBuilderException {
    if (values == null)
      throw new IllegalArgumentException(invalidParam("values", null));

    return newURI(buildFromParameters(values, false, false, encodeSlashInPath).toString());
  }

  @Override
  public URI buildFromEncodedMap(final Map<String,?> values) throws IllegalArgumentException, UriBuilderException {
    if (values == null)
      throw new IllegalArgumentException(invalidParam("values", null));

    return newURI(buildFromParameters(values, true, false, false).toString());
  }

  @Override
  public UriBuilder matrixParam(final String name, final Object ... values) throws IllegalArgumentException {
    if (name == null)
      throw new IllegalArgumentException(invalidParam("name", null));

    if (values == null)
      throw new IllegalArgumentException(invalidParam("values", null));

    if (path == null)
      path = "";

    if (values.length != 0) {
      final StringBuilder builder = new StringBuilder();
      for (final Object value : values) {
        if (value == null)
          throw new IllegalArgumentException(invalid("value", null));

        builder.append(';').append(UriEncoder.MATRIX.encode(name)).append("=").append(UriEncoder.MATRIX.encode(value.toString()));
      }

      path += builder;
    }

    return this;
  }

  @Override
  public UriBuilder replaceMatrixParam(final String name, final Object ... values) throws IllegalArgumentException {
    if (name == null)
      throw new IllegalArgumentException(invalidParam("name", null));

    if (path == null) {
      if (values != null && values.length > 0)
        return matrixParam(name, values);

      return this;
    }

    // remove all path param expressions so we don't accidentally start
    // replacing within a regular expression
    final ArrayList<String> pathParams = new ArrayList<>();

    final StringBuilder segment = new StringBuilder();
    final boolean foundParam = UriEncoder.savePathParams(this.path, segment, pathParams);
    path = segment.toString();

    // Find last path segment
    int start = path.lastIndexOf('/');
    if (start < 0)
      start = 0;

    final int matrixIndex = path.indexOf(';', start);
    if (matrixIndex > -1) {
      final String matrixParams = path.substring(matrixIndex + 1);
      path = path.substring(0, matrixIndex);

      final MultivaluedHashMap<String,String> map = new MultivaluedHashMap<>();
      final String[] params = matrixParams.split(";");
      for (final String param : params) {
        final int index = param.indexOf('=');
        if (index >= 0)
          map.add(param.substring(0, index), index + 1 < param.length() ? param.substring(index + 1) : "");
        else
          map.add(param, null);
      }

      map.remove(name);
      for (final String theName : map.keySet()) {
        final List<String> vals = map.get(theName);
        for (final Object val : vals) {
          path += ";" + theName;
          if (val != null)
            path += "=" + val;
        }
      }
    }

    if (values != null && values.length > 0)
      matrixParam(name, values);

    // put back all path param expressions
    if (foundParam) {
      final Matcher matcher = UriEncoder.PARAM_REPLACEMENT.matcher(path);
      final StringBuilder builder = new StringBuilder();
      int from = 0;
      for (int i = 0; matcher.find(); ++i, from = matcher.end()) {
        builder.append(this.path, from, matcher.start());
        builder.append(pathParams.get(i));
      }

      builder.append(this.path, from, this.path.length());
      path = builder.toString();
    }

    return this;
  }

  @Override
  public UriBuilder queryParam(final String name, final Object ... values) throws IllegalArgumentException {
    if (name == null)
      throw new IllegalArgumentException(invalidParam("name", null));

    if (values == null)
      throw new IllegalArgumentException(invalidParam("values", null));

    final StringBuilder builder = new StringBuilder();
    if (query != null)
      builder.append(query).append('&');

    for (int i = 0; i < values.length; ++i) {
      if (values[i] == null)
        throw new IllegalArgumentException(invalid("value", null));

      if (i > 0)
        builder.append('&');

      builder.append(UriEncoder.QUERY_PARAM.encode(name)).append('=').append(UriEncoder.QUERY_PARAM.encode(values[i].toString()));
    }

    query = builder.toString();
    return this;
  }

  @Override
  public UriBuilder replaceQueryParam(final String name, final Object ... values) throws IllegalArgumentException {
    if (name == null)
      throw new IllegalArgumentException(invalidParam("name", null));

    if (query == null || query.isEmpty())
      return values == null || values.length == 0 ? this : queryParam(name, values);

    final String[] params = query.split("&");
    query = null;

    final String encodedName = UriEncoder.QUERY_PARAM.encode(name);
    for (final String param : params) {
      final int eq = param.indexOf('=');
      if (eq >= 0) {
        final String paramName = param.substring(0, eq);
        if (paramName.equals(encodedName))
          continue;
      }
      else if (param.equals(encodedName)) {
        continue;
      }

      if (query == null)
        query = "";
      else
        query += "&";

      query += param;
    }

    return values == null || values.length == 0 ? this : queryParam(name, values);
  }

  @Override
  public UriBuilder segment(final String ... segments) throws IllegalArgumentException {
    if (segments == null)
      throw new IllegalArgumentException(invalidParam("segments", null));

    for (final String segment : segments) {
      if (segment == null)
        throw new IllegalArgumentException(invalid("segment", null));

      path(UriEncoder.PATH_SEGMENT.encode(segment));
    }

    return this;
  }

  @Override
  public URI buildFromEncoded(final Object ... values) throws IllegalArgumentException, UriBuilderException {
    if (values == null)
      throw new IllegalArgumentException(invalidParam("values", null));

    return newURI(buildFromParameters(new TemplateParametersMap(values), true, false, false).toString());
  }

  @Override
  public UriBuilder replacePath(final String path) {
    this.path = path == null ? null : UriEncoder.PATH.encode(path);
    return this;
  }

  @Override
  public String toTemplate() {
    return buildFromParameters(null, true, true, true).toString();
  }

  @Override
  public UriBuilder resolveTemplateFromEncoded(final String name, final Object value) throws IllegalArgumentException {
    if (name == null)
      throw new IllegalArgumentException(invalidParam("name", null));

    if (value == null)
      throw new IllegalArgumentException(invalidParam("value", null));

    return uri(buildFromParameters(Collections.singletonMap(name, value), true, true, true));
  }

  @Override
  public UriBuilder resolveTemplates(final Map<String,Object> templateValues) throws IllegalArgumentException {
    return resolveTemplates(templateValues, true);
  }

  @Override
  public UriBuilder resolveTemplate(final String name, final Object value) throws IllegalArgumentException {
    return resolveTemplate(name, value, true);
  }

  @Override
  public UriBuilder resolveTemplate(final String name, final Object value, final boolean encodeSlashInPath) throws IllegalArgumentException {
    if (name == null)
      throw new IllegalArgumentException(invalidParam("name", null));

    if (value == null)
      throw new IllegalArgumentException(invalidParam("value", null));

    return resolveTemplates(Collections.singletonMap(name, value), encodeSlashInPath);
  }

  @Override
  public UriBuilder resolveTemplates(final Map<String,Object> templateValues, final boolean encodeSlashInPath) throws IllegalArgumentException {
    if (templateValues == null)
      throw new IllegalArgumentException(invalidParam("templateValues", null));

    if (templateValues.containsKey(null))
      throw new IllegalArgumentException(invalidParam("key in templateValues map", null));

    return uri(buildFromParameters(templateValues, false, true, encodeSlashInPath));
  }

  @Override
  public UriBuilder resolveTemplatesFromEncoded(final Map<String,Object> templateValues) throws IllegalArgumentException {
    return resolveTemplates(templateValues, true);
  }

  @Override
  public UriBuilder clone() {
    final UriBuilderImpl clone = new UriBuilderImpl();
    clone.host = this.host;
    clone.scheme = this.scheme;
    clone.port = this.port;
    clone.userInfo = this.userInfo;
    clone.path = this.path;
    clone.query = this.query;
    clone.fragment = this.fragment;
    clone.ssp = this.ssp;
    clone.authority = this.authority;
    return clone;
  }
}