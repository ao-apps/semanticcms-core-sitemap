/*
 * semanticcms-core-sitemap - Automatic sitemaps for SemanticCMS.
 * Copyright (C) 2023  AO Industries, Inc.
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

import java.net.MalformedURLException;
import java.util.Set;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

/**
 * Dynamically adds a generated to /robots.txt only when no resource exists at /robots.txt.
 *
 * @see SiteMapRobotsTxtServlet
 * @see ServletContext#getResource(java.lang.String)
 */
public class SiteMapRobotsTxtInitializer implements ServletContainerInitializer {

  @Override
  public void onStartup(Set<Class<?>> set, ServletContext servletContext) throws ServletException {
    try {
      if (servletContext.getResource(SiteMapRobotsTxtServlet.SERVLET_PATH) == null) {
        ServletRegistration.Dynamic registration = servletContext.addServlet(
            SiteMapRobotsTxtServlet.class.getName(),
            SiteMapRobotsTxtServlet.class
        );
        registration.addMapping(SiteMapRobotsTxtServlet.SERVLET_PATH);
        registration.setLoadOnStartup(1);
      }
    } catch (MalformedURLException e) {
      throw new ServletException(e);
    }
  }
}
