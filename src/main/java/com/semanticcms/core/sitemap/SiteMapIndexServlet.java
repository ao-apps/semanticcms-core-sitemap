/*
 * semanticcms-core-sitemap - Automatic sitemaps for SemanticCMS.
 * Copyright (C) 2016, 2017, 2018, 2019, 2020, 2021, 2022  AO Industries, Inc.
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

import static com.aoapps.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import static com.aoapps.encoding.TextInXhtmlEncoder.textInXhtmlEncoder;

import com.aoapps.lang.concurrent.ExecutionExceptions;
import com.aoapps.lang.io.ContentType;
import com.aoapps.net.URIEncoder;
import com.aoapps.servlet.attribute.AttributeEE;
import com.aoapps.servlet.attribute.ScopeEE;
import com.aoapps.servlet.http.Canonical;
import com.aoapps.servlet.http.HttpServletUtil;
import com.aoapps.servlet.subrequest.HttpServletSubRequest;
import com.aoapps.servlet.subrequest.HttpServletSubResponse;
import com.aoapps.servlet.subrequest.UnmodifiableCopyHttpServletRequest;
import com.aoapps.servlet.subrequest.UnmodifiableCopyHttpServletResponse;
import com.aoapps.tempfiles.TempFileContext;
import com.aoapps.tempfiles.servlet.TempFileContextEE;
import com.semanticcms.core.model.Book;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.servlet.CaptureLevel;
import com.semanticcms.core.servlet.CapturePage;
import com.semanticcms.core.servlet.ConcurrencyCoordinator;
import com.semanticcms.core.servlet.SemanticCMS;
import com.semanticcms.core.servlet.View;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.ReadableInstant;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

/**
 * Creates a site map index of all per-book sitemaps.
 */
@WebServlet(SiteMapIndexServlet.SERVLET_PATH)
public class SiteMapIndexServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  private static final Logger logger = Logger.getLogger(SiteMapIndexServlet.class.getName());

  public static final String SERVLET_PATH = "/sitemap-index.xml";

  private static final String CONTENT_TYPE = ContentType.XML;

  private static final Charset ENCODING = StandardCharsets.UTF_8;

  /**
   * The sitemap locations are resolved at the beginning of the request and
   * used by both {@link #getLastModified(javax.servlet.http.HttpServletRequest)}
   * and {@link #doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}.
   */
  private static final ScopeEE.Request.Attribute<SortedSet<SiteMapUrl>> LOCS_ATTRIBUTE =
      ScopeEE.REQUEST.attribute(SiteMapIndexServlet.class.getName() + ".locs");

  /**
   * Checks if the sitemap has at least one page.
   * This version implemented as a traversal.
   */
  private static boolean hasSiteMapUrl(
      final ServletContext servletContext,
      final HttpServletRequest req,
      final HttpServletResponse resp,
      final SortedSet<View> views,
      final Book book,
      PageRef pageRef
  ) throws ServletException, IOException {
    Boolean result = CapturePage.traversePagesAnyOrder(
        servletContext,
        req,
        resp,
        pageRef,
        CaptureLevel.META,
        page -> {
          // TODO: Chance for more concurrency here by view?
          for (View view : views) {
            if (
                view.getAllowRobots(servletContext, req, resp, page)
                    && view.isApplicable(servletContext, req, resp, page)
            ) {
              return true;
            }
          }
          return null;
        },
        Page::getChildRefs,
        childPage -> book.equals(childPage.getBook())
    );
    assert result == null || result : "Should always be null or true";
    return result != null;
  }

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    AttributeEE.Request<SortedSet<SiteMapUrl>> locsAttribute = LOCS_ATTRIBUTE.context(req);
    SortedSet<SiteMapUrl> old = locsAttribute.get();
    try {
      final ServletContext servletContext = getServletContext();
      SemanticCMS semanticCms = SemanticCMS.getInstance(servletContext);
      final SortedSet<View> views = semanticCms.getViews();

      SortedSet<SiteMapUrl> locs = new TreeSet<>();
        {
          Collection<Book> books = semanticCms.getBooks().values();
          int numBooks = books.size();
          if (
              numBooks > 1
                  && ConcurrencyCoordinator.useConcurrentSubrequests(req)
          ) {
            // Concurrent implementation
            final HttpServletRequest threadSafeReq = new UnmodifiableCopyHttpServletRequest(req);
            final HttpServletResponse threadSafeResp = new UnmodifiableCopyHttpServletResponse(resp);
            final TempFileContext tempFileContext = TempFileContextEE.get(req);
            List<Book> booksWithSiteMapUrl;
              {
                List<Callable<Boolean>> tasks = new ArrayList<>(numBooks);
                  {
                    for (final Book book : books) {
                      tasks.add(
                          () -> {
                            HttpServletRequest subrequest = new HttpServletSubRequest(threadSafeReq);
                            HttpServletResponse subresponse = new HttpServletSubResponse(threadSafeResp, tempFileContext);
                            if (logger.isLoggable(Level.FINE)) {
                              logger.log(
                                  Level.FINE,
                                  "called, subrequest={0}, book={1}",
                                  new Object[]{
                                      subrequest,
                                      book
                                  }
                              );
                            }
                            return hasSiteMapUrl(
                                servletContext,
                                subrequest,
                                subresponse,
                                views,
                                book,
                                book.getContentRoot()
                            );
                          }
                      );
                    }
                  }
                List<Boolean> results;
                try {
                  results = semanticCms.getExecutors().getPerProcessor().callAll(tasks);
                } catch (InterruptedException e) {
                  // Restore the interrupted status
                  Thread.currentThread().interrupt();
                  throw new ServletException(e);
                } catch (ExecutionException e) {
                  // Maintain expected exception types while not losing stack trace
                  ExecutionExceptions.wrapAndThrow(e, IOException.class, IOException::new);
                  throw new ServletException(e);
                }
                // Now find the last modified with concurrency
                booksWithSiteMapUrl = new ArrayList<>(numBooks);
                  {
                    int i = 0;
                    for (Book book : books) {
                      if (results.get(i++)) {
                        booksWithSiteMapUrl.add(book);
                      }
                    }
                    assert i == numBooks;
                  }
              }
              int booksWithSiteMapUrlSize = booksWithSiteMapUrl.size();
              if (booksWithSiteMapUrlSize > 0) {
                if (booksWithSiteMapUrlSize > 1) {
                  // Concurrent implementation
                  List<Callable<ReadableInstant>> lastModifiedTasks = new ArrayList<>(booksWithSiteMapUrlSize);
                    {
                      for (final Book book : booksWithSiteMapUrl) {
                        lastModifiedTasks.add(
                            () -> {
                              HttpServletRequest subrequest = new HttpServletSubRequest(threadSafeReq);
                              HttpServletResponse subresponse = new HttpServletSubResponse(threadSafeResp, tempFileContext);
                              if (logger.isLoggable(Level.FINE)) {
                                logger.log(
                                    Level.FINE,
                                    "called, subrequest={0}, book={1}",
                                    new Object[]{
                                        subrequest,
                                        book
                                    }
                                );
                              }
                              return SiteMapServlet.getLastModified(
                                  servletContext,
                                  subrequest,
                                  subresponse,
                                  views,
                                  book
                              );
                            }
                        );
                      }
                    }
                  List<ReadableInstant> lastModifieds;
                  try {
                    lastModifieds = semanticCms.getExecutors().getPerProcessor().callAll(lastModifiedTasks);
                  } catch (InterruptedException e) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt();
                    throw new ServletException(e);
                  } catch (ExecutionException e) {
                    // Maintain expected exception types while not losing stack trace
                    ExecutionExceptions.wrapAndThrow(e, IOException.class, IOException::new);
                    throw new ServletException(e);
                  }
                  for (int i = 0; i < booksWithSiteMapUrlSize; i++) {
                    locs.add(
                        new SiteMapUrl(
                            booksWithSiteMapUrl.get(i).getPathPrefix(),
                            lastModifieds.get(i)
                        )
                    );
                  }
                } else {
                  // Single implementation
                  Book book = booksWithSiteMapUrl.get(0);
                  locs.add(
                      new SiteMapUrl(
                          book.getPathPrefix(),
                          SiteMapServlet.getLastModified(
                              servletContext,
                              req,
                              resp,
                              views,
                              book
                          )
                      )
                  );
                }
              } else {
                // Nothing to do
              }
          } else {
            // Sequential implementation
            for (Book book : books) {
              if (
                  hasSiteMapUrl(
                      servletContext,
                      req,
                      resp,
                      views,
                      book,
                      book.getContentRoot()
                  )
              ) {
                locs.add(
                    new SiteMapUrl(
                        book.getPathPrefix(),
                        SiteMapServlet.getLastModified(
                            servletContext,
                            req,
                            resp,
                            views,
                            book
                        )
                    )
                );
              }
            }
          }
        }
      locsAttribute.set(locs);
      super.service(req, resp);
    } finally {
      locsAttribute.set(old);
    }
  }

  private static SortedSet<SiteMapUrl> getLocs(HttpServletRequest req) {
    SortedSet<SiteMapUrl> locs = LOCS_ATTRIBUTE.context(req).get();
    if (locs == null) {
      throw new IllegalStateException("Request attribute not set: " + LOCS_ATTRIBUTE.getName());
    }
    return locs;
  }

  /**
   * Last modified is known only when the last modified is known for all books,
   * and it is the most recent of all the per-book last modified.
   * <p>
   * Note: This depends on the nulls-last ordering of {@link SiteMapUrl#compareTo(com.semanticcms.core.sitemap.SiteMapUrl)}.
   * </p>
   */
  @Override
  protected long getLastModified(HttpServletRequest req) {
    SortedSet<SiteMapUrl> locs = getLocs(req);
    if (locs.isEmpty()) {
      return -1;
    }
    ReadableInstant last = locs.last().getLastmod();
    if (last == null) {
      return -1;
    }
    ReadableInstant first = locs.first().getLastmod();
    return first == null ? -1 : first.getMillis();
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    final SortedSet<SiteMapUrl> locs = getLocs(req);

    final DateTimeFormatter iso8601 = ISODateTimeFormat.dateTime();

    resp.resetBuffer();
    resp.setContentType(CONTENT_TYPE);
    resp.setCharacterEncoding(ENCODING.name());
    PrintWriter out = resp.getWriter();

    out.println("<?xml version=\"1.0\" encoding=\"" + ENCODING + "\"?>");
    out.println("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
    for (SiteMapUrl loc : locs) {
      out.println("    <sitemap>");
      out.print("        <loc>");
      URIEncoder.encodeURI(// Encode again to force RFC 3986 US-ASCII
          Canonical.encodeCanonicalURL(
              resp,
              HttpServletUtil.getAbsoluteURL(
                  req,
                  URIEncoder.encodeURI(
                      loc.getLoc() + SiteMapServlet.SERVLET_PATH
                  )
              )
          ),
          textInXhtmlEncoder,
          out
      );
      out.println("</loc>");
      ReadableInstant lastmod = loc.getLastmod();
      if (lastmod != null) {
        out.print("        <lastmod>");
        encodeTextInXhtml(iso8601.print(lastmod), out);
        out.println("</lastmod>");
      }
      out.println("    </sitemap>");
    }
    out.println("</sitemapindex>");
  }
}
