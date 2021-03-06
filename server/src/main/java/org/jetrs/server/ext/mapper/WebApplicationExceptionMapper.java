/* Copyright (c) 2018 OpenJAX
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

package org.jetrs.server.ext.mapper;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {
  private final boolean verbose;

  public WebApplicationExceptionMapper(final boolean verbose) {
    this.verbose = verbose;
  }

  public WebApplicationExceptionMapper() {
    this(true);
  }

  @Override
  public Response toResponse(final WebApplicationException exception) {
    try (final Response response = exception.getResponse()) {
      final int status = response.getStatus();
      final StringBuilder builder = new StringBuilder("{\"status\":").append(status);
      if (verbose) {
        final String message = exception.getMessage();
        if (message != null) {
          final String prefix = "HTTP " + status + " ";
          builder.append(",\"message\":\"").append(message.startsWith(prefix) ? message.substring(prefix.length()) : message).append('"');
        }
      }

      return Response.fromResponse(response).entity(builder.append('}').toString()).build();
    }
  }
}