/*
 * semanticcms-core-sitemap - Automatic sitemaps for SemanticCMS.
 * Copyright (C) 2019  AO Industries, Inc.
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
 * along with semanticcms-core-sitemap.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.core.sitemap;

import com.aoindustries.util.StringUtility;
import org.joda.time.ReadableInstant;

/**
 * One URL within a sitemap.
 */
class SiteMapUrl implements Comparable<SiteMapUrl> {

	private final String loc;
	private final ReadableInstant lastmod;

	SiteMapUrl(String loc, ReadableInstant lastmod) {
		this.loc = loc;
		this.lastmod = lastmod;
	}

	String getLoc() {
		return loc;
	}

	ReadableInstant getLastmod() {
		return lastmod;
	}

	/**
	 * Ordered by most recent first (with unknown modified times last), then
	 * by loc;
	 * <p>
	 * Note: The ordering of nulls last is required by {@link SiteMapIndexServlet#getLastModified(javax.servlet.http.HttpServletRequest)}.
	 * </p>
	 */
	@Override
	public int compareTo(SiteMapUrl o) {
		if(lastmod != null) {
			if(o.lastmod != null) {
				int diff = lastmod.compareTo(o.lastmod);
				if(diff != 0) return -diff;
			} else {
				// nulls last
				return -1;
			}
		} else {
			if(o.lastmod != null) {
				// nulls last
				return 1;
			}
		}
		return StringUtility.compareToIgnoreCaseCarefulEquals(loc, o.loc);
	}
}
