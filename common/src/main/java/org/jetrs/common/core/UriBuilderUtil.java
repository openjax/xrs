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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.UriBuilderException;

final class UriBuilderUtil {
  private static final String URI_PARAM_NAME_REGEX = "\\w[\\w.-]*";
  private static final String URI_PARAM_REGEX_REGEX = "[^{}][^{}]*";
  private static final String URI_PARAM_REGEX = "\\{\\s*(" + URI_PARAM_NAME_REGEX + ")\\s*(:\\s*(" + URI_PARAM_REGEX_REGEX + "))?\\}";
  private static final Pattern URI_PARAM_PATTERN = Pattern.compile(URI_PARAM_REGEX);

  @SuppressWarnings("unlikely-arg-type")
  static final class TemplateParametersMap extends HashMap<String,Object> {
    private static final long serialVersionUID = 857530441505719224L;
    private final Object[] parameterValues;
    private int index;

    TemplateParametersMap(final Object[] parameterValues) {
      super(parameterValues.length);
      this.parameterValues = parameterValues;
    }

    @Override
    public Object get(final Object key) {
      if (super.containsKey(key))
        return super.get(key);

      if (index == parameterValues.length)
        return null;

      final Object value = parameterValues[index++];
      put((String)key, value);
      return value;
    }

    @Override
    public boolean containsKey(final Object key) {
      get(key);
      return super.containsKey(key);
    }

    @Override
    public TemplateParametersMap clone() {
      return (TemplateParametersMap)super.clone();
    }
  }

  static String invalid(final String name, final Object value) {
    return "Invalid " + name + ": " + value;
  }

  static String invalidParam(final String parameter, final Object value) {
    return "Invalid " + parameter + " parameter: " + value;
  }

  static String pathParameterNotProvided(final String param) {
    return "Path parameter " + param + " is not provided by the parameter map";
  }

  static URI newURI(final String uri) {
    try {
      return new URI(uri);
    }
    catch (final URISyntaxException e) {
      throw new UriBuilderException("Failed to create URI: " + uri, e);
    }
  }

  static void replaceParameter(final Map<String,?> paramMap, final boolean fromEncodedMap, final boolean isTemplate, final String string, final StringBuilder builder, final UriEncoder uriEncoder) {
    if (string.indexOf('{') == -1) {
      builder.append(string);
      return;
    }

    int start = 0;
    for (final Matcher matcher = URI_PARAM_PATTERN.matcher(UriEncoder.replaceBraces(string)); matcher.find(); start = matcher.end()) {
      builder.append(string, start, matcher.start());
      final String param = matcher.group(1);
      if (paramMap == null || !paramMap.containsKey(param)) {
        if (!isTemplate)
          throw new IllegalArgumentException(pathParameterNotProvided(param));

        builder.append(matcher.group());
        continue;
      }

      final Object value = paramMap.get(param);
      if (value == null)
        throw new IllegalArgumentException(invalidParam("value for template", null));

      if (fromEncodedMap)
        builder.append(uriEncoder.encodeSaveEncodings(value.toString()));
      else
        builder.append(uriEncoder.encodeAsIs(value.toString()));
    }

    builder.append(string, start, string.length());
  }

  static String appendPath(final String path, final boolean encode, final String ... segments) {
    final StringBuilder builder = path != null ? new StringBuilder(path) : new StringBuilder();
    for (String segment : segments) {
      if ("".equals(segment))
        continue;

      if (builder.length() > 0 && builder.charAt(builder.length() - 1) == '/') {
        if (segment.startsWith("/")) {
          segment = segment.substring(1);
          if ("".equals(segment))
            continue;
        }

        builder.append(encode ? UriEncoder.PATH.encode(segment) : segment);
      }
      else {
        if (encode)
          segment = UriEncoder.PATH.encode(segment);

        builder.append(builder.length() == 0 || segment.startsWith("/") ? segment : "/" + segment);
      }
    }

    return builder.toString();
  }

  private UriBuilderUtil() {
  }
}