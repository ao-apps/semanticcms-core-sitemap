/*
 * semanticcms-sitemap-servlet - Automatic sitemaps for web page content in a Servlet environment.
 * Copyright (C) 2016  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-sitemap-servlet.
 *
 * semanticcms-sitemap-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-sitemap-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-sitemap-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.sitemap.servlet;

import com.semanticcms.core.model.Book;
import com.semanticcms.core.servlet.SemanticCMS;
import java.util.Set;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.annotation.HandlesTypes;

/**
 * Dynamically adds the SiteMapServlet to /sitemap.xml on each book.
 */
@HandlesTypes(SiteMapServlet.class)
public class SiteMapInitializer implements ServletContainerInitializer {

	@Override
	public void onStartup(Set<Class<?>> set, ServletContext servletContext) throws ServletException {
		ServletRegistration.Dynamic registration = servletContext.addServlet(
			SiteMapServlet.class.getName(),
			SiteMapServlet.class
		);
		for(Book book : SemanticCMS.getInstance(servletContext).getBooks().values()) {
			registration.addMapping(book.getPathPrefix() + SiteMapServlet.SERVLET_PATH);
		}
	}
}
