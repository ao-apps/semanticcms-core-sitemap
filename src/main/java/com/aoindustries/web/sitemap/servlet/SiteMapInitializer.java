/*
 * ao-web-sitemap-servlet - Automatic sitemaps for web page content in a Servlet environment.
 * Copyright (C) 2016  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-web-sitemap-servlet.
 *
 * ao-web-sitemap-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-web-sitemap-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-web-sitemap-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.web.sitemap.servlet;

import com.aoindustries.web.page.servlet.BooksContextListener;
import com.semanticcms.core.model.Book;
import java.io.IOException;
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
		try {
			ServletRegistration.Dynamic registration = servletContext.addServlet(
				SiteMapServlet.class.getName(),
				SiteMapServlet.class
			);
			for(Book book : BooksContextListener.loadBooks(servletContext).getBooks().values()) {
				registration.addMapping(book.getPathPrefix() + SiteMapServlet.SERVLET_PATH);
			}
		} catch(IOException e) {
			throw new ServletException(e);
		}
	}
}
