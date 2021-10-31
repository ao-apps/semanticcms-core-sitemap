/*
 * semanticcms-core-sitemap - Automatic sitemaps for SemanticCMS.
 * Copyright (C) 2016, 2017  AO Industries, Inc.
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

import com.semanticcms.core.model.Book;
import com.semanticcms.core.servlet.SemanticCMS;
import java.util.Set;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

/**
 * Dynamically adds the SiteMapServlet to /sitemap.xml on each book.
 */
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
