/*
 * semanticcms-core-sitemap - Automatic sitemaps for SemanticCMS.
 * Copyright (C) 2021, 2022, 2023, 2025  AO Industries, Inc.
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
module com.semanticcms.core.sitemap {
  exports com.semanticcms.core.sitemap;
  provides javax.servlet.ServletContainerInitializer with com.semanticcms.core.sitemap.SiteMapInitializer;
  // Direct
  requires com.aoapps.concurrent; // <groupId>com.aoapps</groupId><artifactId>ao-concurrent</artifactId>
  requires com.aoapps.encoding; // <groupId>com.aoapps</groupId><artifactId>ao-encoding</artifactId>
  requires com.aoapps.lang; // <groupId>com.aoapps</groupId><artifactId>ao-lang</artifactId>
  requires com.aoapps.net.types; // <groupId>com.aoapps</groupId><artifactId>ao-net-types</artifactId>
  requires com.aoapps.servlet.subrequest; // <groupId>com.aoapps</groupId><artifactId>ao-servlet-subrequest</artifactId>
  requires com.aoapps.servlet.util; // <groupId>com.aoapps</groupId><artifactId>ao-servlet-util</artifactId>
  requires com.aoapps.tempfiles; // <groupId>com.aoapps</groupId><artifactId>ao-tempfiles</artifactId>
  requires com.aoapps.tempfiles.servlet; // <groupId>com.aoapps</groupId><artifactId>ao-tempfiles-servlet</artifactId>
  requires org.apache.commons.lang3; // <groupId>org.apache.commons</groupId><artifactId>commons-lang3</artifactId>
  requires javax.servlet.api; // <groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId>
  requires com.semanticcms.core.model; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-core-model</artifactId>
  requires com.semanticcms.core.servlet; // <groupId>com.semanticcms</groupId><artifactId>semanticcms-core-servlet</artifactId>
  requires static com.github.spotbugs.annotations; // <groupId>com.github.spotbugs</groupId><artifactId>spotbugs-annotations</artifactId>
  // Java SE
  requires java.logging;
}
