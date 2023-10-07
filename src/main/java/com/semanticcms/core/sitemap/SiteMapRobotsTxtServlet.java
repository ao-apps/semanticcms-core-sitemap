/*
 * semanticcms-core-sitemap - Automatic sitemaps for SemanticCMS.
 * Copyright (C) 2016, 2019, 2020, 2021, 2022, 2023  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-core-sitemap.
 *
 * semanticcms-core-sitemap is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-core-sitemap is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-core-sitemap.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.semanticcms.core.sitemap;

import com.aoapps.lang.io.ContentType;
import com.aoapps.net.URIEncoder;
import com.aoapps.servlet.http.Canonical;
import com.aoapps.servlet.http.HttpServletUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Generates a /robots.txt including the sitemap index
 *
 * @see SiteMapRobotsTxtInitializer
 */
public class SiteMapRobotsTxtServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  public static final String SERVLET_PATH = "/robots.txt";

  private static final String CONTENT_TYPE = ContentType.TEXT;

  private static final Charset ENCODING = StandardCharsets.UTF_8;

  /**
   * The modified time is based on the modified time of the class file for this servlet.
   * This is preferred over build timestamps in support of reproducible builds.
   */
  private static final long LAST_MODIFIED;

  static {
    try {
      String classFilename = SiteMapRobotsTxtServlet.class.getSimpleName() + ".class";
      URL classFile = SiteMapRobotsTxtServlet.class.getResource(classFilename);
      if (classFile == null) {
        throw new IOException("Unable to find class file: " + classFilename);
      }
      URLConnection conn = classFile.openConnection();
      conn.setUseCaches(false);
      long lastModified = conn.getLastModified();
      conn.getInputStream().close();
      if (lastModified == 0) {
        throw new IOException("Unknown last-modified in class file: " + classFilename);
      }
      LAST_MODIFIED = lastModified;
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Override
  protected long getLastModified(HttpServletRequest req) {
    return SiteMapIndexServlet.truncateToSecond(LAST_MODIFIED);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    resp.resetBuffer();
    resp.setContentType(CONTENT_TYPE);
    resp.setCharacterEncoding(ENCODING.name());
    PrintWriter out = resp.getWriter();
    out.println("User-agent: *");
    out.println("Allow: /");
    out.print("Sitemap: ");
    URIEncoder.encodeURI(// Encode again to force RFC 3986 US-ASCII
        Canonical.encodeCanonicalURL(
            resp,
            HttpServletUtil.getAbsoluteURL(
                req,
                URIEncoder.encodeURI(
                    SiteMapIndexServlet.SERVLET_PATH
                )
            )
        ),
        out
    );
    out.println();
  }
}
