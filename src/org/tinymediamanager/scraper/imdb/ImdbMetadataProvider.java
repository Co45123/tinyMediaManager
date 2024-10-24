/*
 * Copyright 2012 - 2015 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.scraper.imdb;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Constants;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.scraper.Certification;
import org.tinymediamanager.scraper.CountryCode;
import org.tinymediamanager.scraper.IMediaMetadataProvider;
import org.tinymediamanager.scraper.MediaArtwork;
import org.tinymediamanager.scraper.MediaArtwork.MediaArtworkType;
import org.tinymediamanager.scraper.MediaCastMember;
import org.tinymediamanager.scraper.MediaCastMember.CastType;
import org.tinymediamanager.scraper.MediaGenres;
import org.tinymediamanager.scraper.MediaLanguages;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaProviderInfo;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.MediaSearchOptions;
import org.tinymediamanager.scraper.MediaSearchOptions.SearchParam;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.MetadataUtil;
import org.tinymediamanager.scraper.tmdb.TmdbMetadataProvider;
import org.tinymediamanager.scraper.util.CachedUrl;

/**
 * The Class ImdbMetadataProvider. A meta data provider for the site imdb.com
 * 
 * @author Manuel Laggner
 */
@SuppressWarnings("PMD")
public class ImdbMetadataProvider implements IMediaMetadataProvider {

  private static MediaProviderInfo     providerInfo  = new MediaProviderInfo(Constants.IMDBID, "imdb.com",
                                                         "Scraper for imdb which is able to scrape movie metadata");
  private static final Logger          LOGGER        = LoggerFactory.getLogger(ImdbMetadataProvider.class);
  private static final ExecutorService executor      = Executors.newFixedThreadPool(4);

  public static final String           CAT_ALL       = "&s=all";
  public static final String           CAT_TITLE     = "&s=tt";
  public static final String           CAT_MOVIES    = "&s=tt&ttype=ft&ref_=fn_ft";
  public static final String           CAT_TV        = "&s=tt&ttype=tv&ref_=fn_tv";
  public static final String           CAT_EPISODE   = "&s=tt&ttype=ep&ref_=fn_ep";
  public static final String           CAT_VIDEOGAME = "&s=tt&ttype=vg&ref_=fn_vg";

  private ImdbSiteDefinition           imdbSite;

  public ImdbMetadataProvider() {
    imdbSite = ImdbSiteDefinition.IMDB_COM;
  }

  @Override
  public MediaProviderInfo getProviderInfo() {
    return providerInfo;
  }

  @Override
  public MediaMetadata getMetadata(MediaScrapeOptions options) throws Exception {
    LOGGER.debug("getMetadata() " + options.toString());
    // check if there is a md in the result
    if (options.getResult() != null && options.getResult().getMetadata() != null) {
      LOGGER.debug("IMDB: getMetadata from cache: " + options.getResult());
      return options.getResult().getMetadata();
    }

    MediaMetadata md = new MediaMetadata(providerInfo.getId());
    String imdbId = "";

    // imdbId from searchResult
    if (options.getResult() != null) {
      imdbId = options.getResult().getIMDBId();
    }

    // imdbid from scraper option
    if (!MetadataUtil.isValidImdbId(imdbId)) {
      imdbId = options.getImdbId();
    }

    if (!MetadataUtil.isValidImdbId(imdbId)) {
      return md;
    }

    LOGGER.debug("IMDB: getMetadata(imdbId): " + imdbId);
    md.setId(MediaMetadata.IMDBID, imdbId);

    ExecutorCompletionService<Document> compSvcImdb = new ExecutorCompletionService<Document>(executor);
    ExecutorCompletionService<MediaMetadata> compSvcTmdb = new ExecutorCompletionService<MediaMetadata>(executor);

    // worker for imdb request (/combined) (everytime from akas.imdb.com)
    // StringBuilder sb = new StringBuilder(imdbSite.getSite());
    StringBuilder sb = new StringBuilder(ImdbSiteDefinition.IMDB_COM.getSite());
    sb.append("title/");
    sb.append(imdbId);
    sb.append("/combined");
    Callable<Document> worker = new ImdbWorker(sb.toString(), options.getLanguage().name(), options.getCountry().getAlpha2());
    Future<Document> futureCombined = compSvcImdb.submit(worker);

    // worker for imdb request (/plotsummary) (from chosen site)
    Future<Document> futurePlotsummary = null;
    sb = new StringBuilder(imdbSite.getSite());
    sb.append("title/");
    sb.append(imdbId);
    sb.append("/plotsummary");

    worker = new ImdbWorker(sb.toString(), options.getLanguage().name(), options.getCountry().getAlpha2());
    futurePlotsummary = compSvcImdb.submit(worker);

    // worker for tmdb request
    Future<MediaMetadata> futureTmdb = null;
    if (options.isScrapeImdbForeignLanguage() || options.isScrapeCollectionInfo()) {
      Callable<MediaMetadata> worker2 = new TmdbWorker(imdbId, options.getLanguage(), options.getCountry());
      futureTmdb = compSvcTmdb.submit(worker2);
    }

    Document doc;
    doc = futureCombined.get();

    /*
     * title and year have the following structure
     * 
     * <div id="tn15title"><h1>Merida - Legende der Highlands <span>(<a href="/year/2012/">2012</a>) <span class="pro-link">...</span> <span
     * class="title-extra">Brave <i>(original title)</i></span> </span></h1> </div>
     */

    // parse title and year
    Element title = doc.getElementById("tn15title");
    if (title != null) {
      Element element = null;
      // title
      Elements elements = title.getElementsByTag("h1");
      if (elements.size() > 0) {
        element = elements.first();
        String movieTitle = cleanString(element.ownText());
        md.storeMetadata(MediaMetadata.TITLE, movieTitle);
      }

      // year
      elements = title.getElementsByTag("span");
      if (elements.size() > 0) {
        element = elements.first();
        String content = element.text();

        // search year
        Pattern yearPattern = Pattern.compile("\\(([0-9]{4})|/\\)");
        Matcher matcher = yearPattern.matcher(content);
        while (matcher.find()) {
          if (matcher.group(1) != null) {
            String movieYear = matcher.group(1);
            md.storeMetadata(MediaMetadata.YEAR, movieYear);
            break;
          }
        }
      }

      // original title
      elements = title.getElementsByAttributeValue("class", "title-extra");
      if (elements.size() > 0) {
        element = elements.first();
        String content = element.text();
        content = content.replaceAll("\\(original title\\)", "").trim();
        md.storeMetadata(MediaMetadata.ORIGINAL_TITLE, content);
      }
    }

    // poster
    Element poster = doc.getElementById("primary-poster");
    if (poster != null) {
      String posterUrl = poster.attr("src");
      posterUrl = posterUrl.replaceAll("SX[0-9]{2,4}_", "SX400_");
      posterUrl = posterUrl.replaceAll("SY[0-9]{2,4}_", "SY400_");
      processMediaArt(md, MediaArtworkType.POSTER, "Poster", posterUrl);
    }

    /*
     * <div class="starbar-meta"> <b>7.4/10</b> &nbsp;&nbsp;<a href="ratings" class="tn15more">52,871 votes</a>&nbsp;&raquo; </div>
     */

    // rating and rating count
    Element ratingElement = doc.getElementById("tn15rating");
    if (ratingElement != null) {
      Elements elements = ratingElement.getElementsByClass("starbar-meta");
      if (elements.size() > 0) {
        Element div = elements.get(0);

        // rating comes in <b> tag
        Elements b = div.getElementsByTag("b");
        if (b.size() == 1) {
          String ratingAsString = b.text();
          Pattern ratingPattern = Pattern.compile("([0-9]\\.[0-9])/10");
          Matcher matcher = ratingPattern.matcher(ratingAsString);
          while (matcher.find()) {
            if (matcher.group(1) != null) {
              float rating = 0;
              try {
                rating = Float.valueOf(matcher.group(1));
              }
              catch (Exception e) {
              }
              md.storeMetadata(MediaMetadata.RATING, rating);
              break;
            }
          }
        }

        // count
        Elements a = div.getElementsByAttributeValue("href", "ratings");
        if (a.size() == 1) {
          String countAsString = a.text().replaceAll("[.,]|votes", "").trim();
          int voteCount = 0;
          try {
            voteCount = Integer.parseInt(countAsString);
          }
          catch (Exception e) {
          }
          md.storeMetadata(MediaMetadata.VOTE_COUNT, voteCount);
        }
      }

      // top250
      elements = ratingElement.getElementsByClass("starbar-special");
      if (elements.size() > 0) {
        Elements a = elements.get(0).getElementsByTag("a");
        if (a.size() > 0) {
          Element anchor = a.get(0);
          Pattern topPattern = Pattern.compile("Top 250: #([0-9]{1,3})");
          Matcher matcher = topPattern.matcher(anchor.ownText());
          while (matcher.find()) {
            if (matcher.group(1) != null) {
              int top250 = 0;
              try {
                top250 = Integer.parseInt(matcher.group(1));
              }
              catch (Exception e) {
              }
              md.storeMetadata(MediaMetadata.TOP_250, top250);
            }
          }
        }
      }
    }

    // parse all items coming by <div class="info">
    Elements elements = doc.getElementsByClass("info");
    for (Element element : elements) {
      // only parse divs
      if (!"div".equals(element.tag().getName())) {
        continue;
      }

      // elements with h5 are the titles of the values
      Elements h5 = element.getElementsByTag("h5");
      if (h5.size() > 0) {
        Element firstH5 = h5.first();
        String h5Title = firstH5.text();

        // release date
        /*
         * <div class="info"><h5>Release Date:</h5><div class="info-content">5 January 1996 (USA)<a class="tn15more inline"
         * href="/title/tt0114746/releaseinfo"
         * onclick="(new Image()).src='/rg/title-tease/releasedates/images/b.gif?link=/title/tt0114746/releaseinfo';"> See more</a>&nbsp;</div></div>
         */
        if (h5Title.matches("(?i)" + ImdbSiteDefinition.IMDB_COM.getReleaseDate() + ".*")) {
          Elements div = element.getElementsByClass("info-content");
          if (div.size() > 0) {
            Element releaseDateElement = div.first();
            String releaseDate = cleanString(releaseDateElement.ownText().replaceAll("»", ""));
            Pattern pattern = Pattern.compile("(.*)\\(.*\\)");
            Matcher matcher = pattern.matcher(releaseDate);
            if (matcher.find()) {
              try {
                SimpleDateFormat sdf = new SimpleDateFormat("d MMM yyyy");
                Date parsedDate = sdf.parse(matcher.group(1));
                sdf = new SimpleDateFormat("dd-MM-yyyy");
                md.storeMetadata(MediaMetadata.RELEASE_DATE, sdf.format(parsedDate));
              }
              catch (Exception e) {
              }
            }
          }
        }

        /*
         * <div class="info"><h5>Tagline:</h5><div class="info-content"> (7) To Defend Us... <a class="tn15more inline"
         * href="/title/tt0472033/taglines" onClick= "(new Image()).src='/rg/title-tease/taglines/images/b.gif?link=/title/tt0472033/taglines';" >See
         * more</a>&nbsp;&raquo; </div></div>
         */
        // tagline
        if (h5Title.matches("(?i)" + ImdbSiteDefinition.IMDB_COM.getTagline() + ".*") && !options.isScrapeImdbForeignLanguage()) {
          Elements div = element.getElementsByClass("info-content");
          if (div.size() > 0) {
            Element taglineElement = div.first();
            String tagline = cleanString(taglineElement.ownText().replaceAll("»", ""));
            md.storeMetadata(MediaMetadata.TAGLINE, tagline);
          }
        }

        /*
         * <div class="info-content"><a href="/Sections/Genres/Animation/">Animation</a> | <a href="/Sections/Genres/Action/">Action</a> | <a
         * href="/Sections/Genres/Adventure/">Adventure</a> | <a href="/Sections/Genres/Fantasy/">Fantasy</a> | <a
         * href="/Sections/Genres/Mystery/">Mystery</a> | <a href="/Sections/Genres/Sci-Fi/">Sci-Fi</a> | <a
         * href="/Sections/Genres/Thriller/">Thriller</a> <a class="tn15more inline" href="/title/tt0472033/keywords" onClick=
         * "(new Image()).src='/rg/title-tease/keywords/images/b.gif?link=/title/tt0472033/keywords';" > See more</a>&nbsp;&raquo; </div>
         */
        // genres are only scraped from akas.imdb.com
        if (h5Title.matches("(?i)" + imdbSite.getGenre() + "(.*)")) {
          Elements div = element.getElementsByClass("info-content");
          if (div.size() > 0) {
            Elements a = div.first().getElementsByTag("a");
            for (Element anchor : a) {
              if (anchor.attr("href").matches("/Sections/Genres/.*")) {
                md.addGenre(getTmmGenre(anchor.ownText()));
              }
            }
          }
        }
        // }

        /*
         * <div class="info"><h5>Runtime:</h5><div class="info-content">162 min | 171 min (special edition) | 178 min (extended cut)</div></div>
         */
        // runtime
        // if (h5Title.matches("(?i)" + imdbSite.getRuntime() + ".*")) {
        if (h5Title.matches("(?i)" + ImdbSiteDefinition.IMDB_COM.getRuntime() + ".*")) {
          Elements div = element.getElementsByClass("info-content");
          if (div.size() > 0) {
            Element taglineElement = div.first();
            String first = taglineElement.ownText().split("\\|")[0];
            String runtimeAsString = cleanString(first.replaceAll("min", ""));
            int runtime = 0;
            try {
              runtime = Integer.parseInt(runtimeAsString);
            }
            catch (Exception e) {
              // try to filter out the first number we find
              Pattern runtimePattern = Pattern.compile("([0-9]{2,3})");
              Matcher matcher = runtimePattern.matcher(runtimeAsString);
              if (matcher.find()) {
                runtime = Integer.parseInt(matcher.group(0));
              }
            }
            md.storeMetadata(MediaMetadata.RUNTIME, runtime);
          }
        }

        /*
         * <div class="info"><h5>Country:</h5><div class="info-content"><a href="/country/fr">France</a> | <a href="/country/es">Spain</a> | <a
         * href="/country/it">Italy</a> | <a href="/country/hu">Hungary</a></div></div>
         */
        // country
        if (h5Title.matches("(?i)Country.*")) {
          Elements a = element.getElementsByTag("a");
          String countries = "";
          for (Element anchor : a) {
            Pattern pattern = Pattern.compile("/country/(.*)");
            Matcher matcher = pattern.matcher(anchor.attr("href"));
            if (matcher.matches()) {
              String country = matcher.group(1);
              if (StringUtils.isNotEmpty(countries)) {
                countries += ", ";
              }
              countries += country.toUpperCase();
            }
          }
          md.storeMetadata(MediaMetadata.COUNTRY, countries);
        }

        /*
         * <div class="info"><h5>Language:</h5><div class="info-content"><a href="/language/en">English</a> | <a href="/language/de">German</a> | <a
         * href="/language/fr">French</a> | <a href="/language/it">Italian</a></div>
         */
        // Spoken languages
        if (h5Title.matches("(?i)Language.*")) {
          Elements a = element.getElementsByTag("a");
          String spokenLanguages = "";
          for (Element anchor : a) {
            Pattern pattern = Pattern.compile("/language/(.*)");
            Matcher matcher = pattern.matcher(anchor.attr("href"));
            if (matcher.matches()) {
              String langu = matcher.group(1);
              if (StringUtils.isNotEmpty(spokenLanguages)) {
                spokenLanguages += ", ";
              }
              spokenLanguages += langu;
            }
          }
          md.storeMetadata(MediaMetadata.SPOKEN_LANGUAGES, spokenLanguages);
        }

        /*
         * <div class="info"><h5>Certification:</h5><div class="info-content"><a href="/search/title?certificates=us:pg">USA:PG</a> <i>(certificate
         * #47489)</i> | <a href="/search/title?certificates=ca:pg">Canada:PG</a> <i>(Ontario)</i> | <a
         * href="/search/title?certificates=au:pg">Australia:PG</a> | <a href="/search/title?certificates=in:u">India:U</a> | <a
         * href="/search/title?certificates=ie:pg">Ireland:PG</a> ...</div></div>
         */
        // certification
        // if (h5Title.matches("(?i)" + imdbSite.getCertification() + ".*")) {
        if (h5Title.matches("(?i)" + ImdbSiteDefinition.IMDB_COM.getCertification() + ".*")) {
          Elements a = element.getElementsByTag("a");
          for (Element anchor : a) {
            // certification for the right country
            if (anchor.attr("href").matches("(?i)/search/title\\?certificates=" + options.getCountry().getAlpha2() + ".*")) {
              Pattern certificationPattern = Pattern.compile(".*:(.*)");
              Matcher matcher = certificationPattern.matcher(anchor.ownText());
              Certification certification = null;
              while (matcher.find()) {
                if (matcher.group(1) != null) {
                  certification = Certification.getCertification(options.getCountry(), matcher.group(1));
                }
              }

              if (certification != null) {
                md.addCertification(certification);
                break;
              }
            }
          }
        }
      }

      /*
       * <div id="director-info" class="info"> <h5>Director:</h5> <div class="info-content"><a href="/name/nm0000416/" onclick=
       * "(new Image()).src='/rg/directorlist/position-1/images/b.gif?link=name/nm0000416/';" >Terry Gilliam</a><br/> </div> </div>
       */
      // director
      if ("director-info".equals(element.id())) {
        Elements a = element.getElementsByTag("a");
        for (Element anchor : a) {
          if (anchor.attr("href").matches("/name/nm.*")) {
            MediaCastMember cm = new MediaCastMember(CastType.DIRECTOR);
            cm.setName(anchor.ownText());
            md.addCastMember(cm);
          }
        }
      }
    }

    /*
     * <table class="cast"> <tr class="odd"><td class="hs"><a href="http://pro.imdb.com/widget/resume_redirect/" onClick=
     * "(new Image()).src='/rg/resume/prosystem/images/b.gif?link=http://pro.imdb.com/widget/resume_redirect/';" ><img src=
     * "http://i.media-imdb.com/images/SF9113d6f5b7cb1533c35313ccd181a6b1/tn15/no_photo.png" width="25" height="31" border="0"></td><td class="nm"><a
     * href="/name/nm0577828/" onclick= "(new Image()).src='/rg/castlist/position-1/images/b.gif?link=/name/nm0577828/';" >Joseph Melito</a></td><td
     * class="ddd"> ... </td><td class="char"><a href="/character/ch0003139/">Young Cole</a></td></tr> <tr class="even"><td class="hs"><a
     * href="/name/nm0000246/" onClick= "(new Image()).src='/rg/title-tease/tinyhead/images/b.gif?link=/name/nm0000246/';" ><img src=
     * "http://ia.media-imdb.com/images/M/MV5BMjA0MjMzMTE5OF5BMl5BanBnXkFtZTcwMzQ2ODE3Mw@@._V1._SY30_SX23_.jpg" width="23" height="32"
     * border="0"></a><br></td><td class="nm"><a href="/name/nm0000246/" onclick=
     * "(new Image()).src='/rg/castlist/position-2/images/b.gif?link=/name/nm0000246/';" >Bruce Willis</a></td><td class="ddd"> ... </td><td
     * class="char"><a href="/character/ch0003139/">James Cole</a></td></tr> <tr class="odd"><td class="hs"><a href="/name/nm0781218/" onClick=
     * "(new Image()).src='/rg/title-tease/tinyhead/images/b.gif?link=/name/nm0781218/';" ><img src=
     * "http://ia.media-imdb.com/images/M/MV5BODI1MTA2MjkxM15BMl5BanBnXkFtZTcwMTcwMDg2Nw@@._V1._SY30_SX23_.jpg" width="23" height="32"
     * border="0"></a><br></td><td class="nm"><a href="/name/nm0781218/" onclick=
     * "(new Image()).src='/rg/castlist/position-3/images/b.gif?link=/name/nm0781218/';" >Jon Seda</a></td><td class="ddd"> ... </td><td
     * class="char"><a href="/character/ch0003143/">Jose</a></td></tr>...</table>
     */
    // cast
    elements = doc.getElementsByClass("cast");
    if (elements.size() > 0) {
      Elements tr = elements.get(0).getElementsByTag("tr");
      for (Element row : tr) {
        Elements td = row.getElementsByTag("td");
        MediaCastMember cm = new MediaCastMember();
        for (Element column : td) {
          // actor thumb
          if (column.hasClass("hs")) {
            Elements img = column.getElementsByTag("img");
            if (img.size() > 0) {
              String thumbUrl = img.get(0).attr("src");
              if (thumbUrl.contains("no_photo.png")) {
                cm.setImageUrl("");
              }
              else {
                thumbUrl = thumbUrl.replaceAll("SX[0-9]{2,4}_", "SX400_");
                thumbUrl = thumbUrl.replaceAll("SY[0-9]{2,4}_", "");
                cm.setImageUrl(thumbUrl);
              }
            }
          }
          // actor name
          if (column.hasClass("nm")) {
            cm.setName(cleanString(column.text()));
          }
          // character
          if (column.hasClass("char")) {
            cm.setCharacter(cleanString(column.text()));
          }
        }
        if (StringUtils.isNotEmpty(cm.getName()) && StringUtils.isNotEmpty(cm.getCharacter())) {
          cm.setType(CastType.ACTOR);
          md.addCastMember(cm);
        }
      }
    }

    Element content = doc.getElementById("tn15content");
    if (content != null) {
      elements = content.getElementsByTag("table");
      for (Element table : elements) {
        // writers
        if (table.text().contains(ImdbSiteDefinition.IMDB_COM.getWriter())) {
          Elements anchors = table.getElementsByTag("a");
          for (Element anchor : anchors) {
            if (anchor.attr("href").matches("/name/nm.*")) {
              MediaCastMember cm = new MediaCastMember(CastType.WRITER);
              cm.setName(anchor.ownText());
              md.addCastMember(cm);
            }
          }
        }

        // producers
        if (table.text().contains(ImdbSiteDefinition.IMDB_COM.getProducers())) {
          Elements rows = table.getElementsByTag("tr");
          for (Element row : rows) {
            if (row.text().contains(ImdbSiteDefinition.IMDB_COM.getProducers())) {
              continue;
            }
            Elements columns = row.children();
            if (columns.size() == 0) {
              continue;
            }
            MediaCastMember cm = new MediaCastMember(CastType.PRODUCER);
            String name = cleanString(columns.get(0).text());
            if (StringUtils.isBlank(name)) {
              continue;
            }
            cm.setName(name);
            if (columns.size() >= 3) {
              cm.setPart(cleanString(columns.get(2).text()));
            }
            md.addCastMember(cm);
          }
        }
      }
    }

    // Production companies
    elements = doc.getElementsByClass("blackcatheader");
    for (Element blackcatheader : elements) {
      if (blackcatheader.ownText().equals(ImdbSiteDefinition.IMDB_COM.getProductionCompanies())) {
        Elements a = blackcatheader.nextElementSibling().getElementsByTag("a");
        StringBuilder productionCompanies = new StringBuilder();
        for (Element anchor : a) {
          if (StringUtils.isNotEmpty(productionCompanies)) {
            productionCompanies.append(", ");
          }
          productionCompanies.append(anchor.ownText());
        }
        md.storeMetadata(MediaMetadata.PRODUCTION_COMPANY, productionCompanies.toString());
        break;
      }
    }

    /*
     * plot from /plotsummary
     */
    // build the url
    doc = null;
    doc = futurePlotsummary.get();

    // imdb.com has another site structure
    if (imdbSite == ImdbSiteDefinition.IMDB_COM) {
      Elements zebraList = doc.getElementsByClass("zebraList");
      if (zebraList != null && !zebraList.isEmpty()) {
        Elements odd = zebraList.get(0).getElementsByClass("odd");
        if (odd.isEmpty()) {
          odd = zebraList.get(0).getElementsByClass("even"); // sometimes imdb has even
        }
        if (odd.size() > 0) {
          Elements p = odd.get(0).getElementsByTag("p");
          if (p.size() > 0) {
            String plot = cleanString(p.get(0).ownText());
            md.storeMetadata(MediaMetadata.PLOT, plot);
          }
        }
      }
    }
    else {
      Element wiki = doc.getElementById("swiki.2.1");
      if (wiki != null) {
        String plot = cleanString(wiki.ownText());
        md.storeMetadata(MediaMetadata.PLOT, plot);
      }
    }

    // title also from chosen site if we are not scraping akas.imdb.com
    if (imdbSite != ImdbSiteDefinition.IMDB_COM) {
      title = doc.getElementById("tn15title");
      if (title != null) {
        Element element = null;
        // title
        elements = title.getElementsByClass("main");
        if (elements.size() > 0) {
          element = elements.first();
          String movieTitle = cleanString(element.ownText());
          md.storeMetadata(MediaMetadata.TITLE, movieTitle);
        }
      }
    }
    // }

    // get data from tmdb?
    if (options.isScrapeImdbForeignLanguage() || options.isScrapeCollectionInfo()) {
      MediaMetadata tmdbMd = futureTmdb.get();
      if (options.isScrapeImdbForeignLanguage() && tmdbMd != null && StringUtils.isNotBlank(tmdbMd.getStringValue(MediaMetadata.PLOT))) {
        // tmdbid
        md.setId(MediaMetadata.TMDBID, tmdbMd.getId(MediaMetadata.TMDBID));
        // title
        md.storeMetadata(MediaMetadata.TITLE, tmdbMd.getStringValue(MediaMetadata.TITLE));
        // original title
        md.storeMetadata(MediaMetadata.ORIGINAL_TITLE, tmdbMd.getStringValue(MediaMetadata.ORIGINAL_TITLE));
        // tagline
        md.storeMetadata(MediaMetadata.TAGLINE, tmdbMd.getStringValue(MediaMetadata.TAGLINE));
        // plot
        md.storeMetadata(MediaMetadata.PLOT, tmdbMd.getStringValue(MediaMetadata.PLOT));
        // collection info
        md.storeMetadata(MediaMetadata.COLLECTION_NAME, tmdbMd.getStringValue(MediaMetadata.COLLECTION_NAME));
        md.storeMetadata(MediaMetadata.TMDBID_SET, tmdbMd.getIntegerValue(MediaMetadata.TMDBID_SET));
      }
      if (options.isScrapeCollectionInfo() && tmdbMd != null) {
        md.storeMetadata(MediaMetadata.TMDBID_SET, tmdbMd.getIntegerValue(MediaMetadata.TMDBID_SET));
        md.storeMetadata(MediaMetadata.COLLECTION_NAME, tmdbMd.getStringValue(MediaMetadata.COLLECTION_NAME));
      }
    }

    // if we have still no original title, take the title
    if (StringUtils.isBlank(md.getStringValue(MediaMetadata.ORIGINAL_TITLE))) {
      md.storeMetadata(MediaMetadata.ORIGINAL_TITLE, md.getStringValue(MediaMetadata.TITLE));
    }

    return md;
  }

  @Override
  public List<MediaSearchResult> search(MediaSearchOptions query) throws Exception {
    LOGGER.debug("search() " + query.toString());
    /*
     * IMDb matches seem to come in several "flavours".
     * 
     * Firstly, if there is one exact match it returns the matching IMDb page.
     * 
     * If that fails to produce a unique hit then a list of possible matches are returned categorised as: Popular Titles (Displaying ? Results) Titles
     * (Exact Matches) (Displaying ? Results) Titles (Partial Matches) (Displaying ? Results)
     * 
     * We should check the Exact match section first, then the poplar titles and finally the partial matches.
     * 
     * Note: That even with exact matches there can be more than 1 hit, for example "Star Trek"
     */

    Pattern imdbIdPattern = Pattern.compile("/title/(tt[0-9]{7})/");

    List<MediaSearchResult> result = new ArrayList<MediaSearchResult>();

    String searchTerm = "";

    if (StringUtils.isNotEmpty(query.get(SearchParam.IMDBID))) {
      searchTerm = query.get(SearchParam.IMDBID);
    }

    if (StringUtils.isEmpty(searchTerm)) {
      searchTerm = query.get(SearchParam.QUERY);
    }

    if (StringUtils.isEmpty(searchTerm)) {
      searchTerm = query.get(SearchParam.TITLE);
    }

    if (StringUtils.isEmpty(searchTerm)) {
      return result;
    }

    // parse out language and coutry from the scraper options
    String language = query.get(SearchParam.LANGUAGE);
    String myear = query.get(SearchParam.YEAR);
    String country = query.get(SearchParam.COUNTRY); // for passing the country to the scrape

    searchTerm = MetadataUtil.removeNonSearchCharacters(searchTerm);

    StringBuilder sb = new StringBuilder(imdbSite.getSite());
    sb.append("find?q=");
    try {
      // search site was everytime in UTF-8
      sb.append(URLEncoder.encode(searchTerm, "UTF-8"));
    }
    catch (UnsupportedEncodingException ex) {
      // Failed to encode the movie name for some reason!
      LOGGER.debug("Failed to encode search term: " + searchTerm);
      sb.append(searchTerm);
    }

    // we need to search for all - otherwise we do not find TV movies
    sb.append(CAT_TITLE);

    LOGGER.debug("========= BEGIN IMDB Scraper Search for: " + sb.toString());
    Document doc;
    try {
      CachedUrl url = new CachedUrl(sb.toString());
      url.addHeader("Accept-Language", getAcceptLanguage(language, country));
      doc = Jsoup.parse(url.getInputStream(), "UTF-8", "");
    }
    catch (Exception e) {
      LOGGER.debug("tried to fetch search response", e);

      // clear Cache
      CachedUrl.removeCachedFileForUrl(sb.toString());

      return result;
    }

    // check if it was directly redirected to the site
    Elements elements = doc.getElementsByAttributeValue("rel", "canonical");
    for (Element element : elements) {
      MediaMetadata md = null;
      // we have been redirected to the movie site
      String movieName = null;
      String movieId = null;

      String href = element.attr("href");
      Matcher matcher = imdbIdPattern.matcher(href);
      while (matcher.find()) {
        if (matcher.group(1) != null) {
          movieId = matcher.group(1);
        }
      }

      // get full information
      if (!StringUtils.isEmpty(movieId)) {
        MediaScrapeOptions options = new MediaScrapeOptions();
        options.setImdbId(movieId);
        options.setLanguage(MediaLanguages.valueOf(language));
        options.setCountry(CountryCode.valueOf(country));
        options.setScrapeCollectionInfo(Boolean.parseBoolean(query.get(SearchParam.COLLECTION_INFO)));
        options.setScrapeImdbForeignLanguage(Boolean.parseBoolean(query.get(SearchParam.IMDB_FOREIGN_LANGUAGE)));
        md = getMetadata(options);
        if (!StringUtils.isEmpty(md.getStringValue(MediaMetadata.TITLE))) {
          movieName = md.getStringValue(MediaMetadata.TITLE);
        }
      }

      // if a movie name/id was found - return it
      if (StringUtils.isNotEmpty(movieName) && StringUtils.isNotEmpty(movieId)) {
        MediaSearchResult sr = new MediaSearchResult(providerInfo.getId());
        sr.setTitle(movieName);
        sr.setIMDBId(movieId);
        sr.setYear(md.getStringValue(MediaMetadata.YEAR));
        sr.setMetadata(md);
        sr.setScore(1);

        // and parse out the poster
        String posterUrl = "";
        Element td = doc.getElementById("img_primary");
        if (td != null) {
          Elements imgs = td.getElementsByTag("img");
          for (Element img : imgs) {
            posterUrl = img.attr("src");
            posterUrl = posterUrl.replaceAll("SX[0-9]{2,4}_", "SX400_");
            posterUrl = posterUrl.replaceAll("SY[0-9]{2,4}_", "SY400_");
            posterUrl = posterUrl.replaceAll("CR[0-9]{1,3},[0-9]{1,3},[0-9]{1,3},[0-9]{1,3}_", "");
          }
        }
        if (StringUtils.isNotBlank(posterUrl)) {
          sr.setPosterUrl(posterUrl);
        }

        result.add(sr);
        return result;
      }
    }

    // parse results
    // elements = doc.getElementsByClass("result_text");
    elements = doc.getElementsByClass("findResult");
    for (Element tr : elements) {
      // we only want the tr's
      if (!"tr".equalsIgnoreCase(tr.tagName())) {
        continue;
      }

      // find the id / name
      String movieName = "";
      String movieId = "";
      String year = "";
      Elements tds = tr.getElementsByClass("result_text");
      for (Element element : tds) {
        // we only want the td's
        if (!"td".equalsIgnoreCase(element.tagName())) {
          continue;
        }

        // filter out unwanted results
        Pattern unwanted = Pattern.compile(".*\\((TV Series|TV Episode|Short|Video Game)\\).*"); // stripped out .*\\(Video\\).*|
        Matcher matcher = unwanted.matcher(element.text());
        if (matcher.find()) {
          continue;
        }

        // is there a localized name? (aka)
        String localizedName = "";
        Elements italics = element.getElementsByTag("i");
        if (italics.size() > 0) {
          localizedName = italics.text().replace("\"", "");
        }

        // get the name inside the link
        Elements anchors = element.getElementsByTag("a");
        for (Element a : anchors) {
          if (StringUtils.isNotEmpty(a.text())) {
            // movie name
            if (StringUtils.isNotBlank(localizedName) && !language.equals("en")) {
              // take AKA as title, but only if not EN
              movieName = localizedName;
            }
            else {
              movieName = a.text();
            }

            // parse id
            String href = a.attr("href");
            matcher = imdbIdPattern.matcher(href);
            while (matcher.find()) {
              if (matcher.group(1) != null) {
                movieId = matcher.group(1);
              }
            }

            // try to parse out the year
            Pattern yearPattern = Pattern.compile("\\(([0-9]{4})|/\\)");
            matcher = yearPattern.matcher(element.text());
            while (matcher.find()) {
              if (matcher.group(1) != null) {
                year = matcher.group(1);
                break;
              }
            }
            break;
          }
        }
      }

      // if an id/name was found - parse the poster image
      String posterUrl = "";
      tds = tr.getElementsByClass("primary_photo");
      for (Element element : tds) {
        Elements imgs = element.getElementsByTag("img");
        for (Element img : imgs) {
          posterUrl = img.attr("src");
          posterUrl = posterUrl.replaceAll("SX[0-9]{2,4}_", "SX400_");
          posterUrl = posterUrl.replaceAll("SY[0-9]{2,4}_", "SY400_");
          posterUrl = posterUrl.replaceAll("CR[0-9]{1,3},[0-9]{1,3},[0-9]{1,3},[0-9]{1,3}_", "");
        }
      }

      // if no movie name/id was found - continue
      if (StringUtils.isEmpty(movieName) || StringUtils.isEmpty(movieId)) {
        continue;
      }

      MediaSearchResult sr = new MediaSearchResult(providerInfo.getId());
      sr.setTitle(movieName);
      sr.setIMDBId(movieId);
      sr.setYear(year);
      sr.setPosterUrl(posterUrl);

      // populate extra args
      MetadataUtil.copySearchQueryToSearchResult(query, sr);

      if (movieId.equals(query.get(SearchParam.IMDBID))) {
        // perfect match
        sr.setScore(1);
      }
      else {
        // compare score based on names
        float score = MetadataUtil.calculateScore(searchTerm, movieName);
        if (posterUrl.isEmpty() || posterUrl.contains("nopicture")) {
          LOGGER.debug("no poster - downgrading score by 0.01");
          score = score - 0.01f;
        }
        if (myear != null && !myear.isEmpty() && !myear.equals("0") && !myear.equals(year)) {
          LOGGER.debug("parsed year does not match search result year - downgrading score by 0.01");
          score = score - 0.01f;
        }
        sr.setScore(score);
      }

      result.add(sr);

      // only get 40 results
      if (result.size() >= 40) {
        break;
      }
    }
    Collections.sort(result);
    Collections.reverse(result);

    return result;
  }

  /*
   * generates the accept-language http header for imdb
   */
  public static String getAcceptLanguage(String language, String country) {
    List<String> languageString = new ArrayList<String>();

    // first: take the preferred language from settings
    if (StringUtils.isNotBlank(language) && StringUtils.isNotBlank(country)) {
      String combined = language + "-" + country;
      languageString.add(combined.toLowerCase());
    }

    // also build langu & default country
    Locale localeFromLanguage = Utils.getLocaleFromLanguage(language);
    if (localeFromLanguage != null) {
      String combined = language + "-" + localeFromLanguage.getCountry().toLowerCase();
      if (!languageString.contains(combined)) {
        languageString.add(combined);
      }
    }

    if (StringUtils.isNotBlank(language)) {
      languageString.add(language.toLowerCase());
    }

    // second: the JRE language
    Locale jreLocale = Locale.getDefault();
    String combined = (jreLocale.getLanguage() + "-" + jreLocale.getCountry()).toLowerCase();
    if (!languageString.contains(combined)) {
      languageString.add(combined);
    }

    if (!languageString.contains(jreLocale.getLanguage().toLowerCase())) {
      languageString.add(jreLocale.getLanguage().toLowerCase());
    }

    // third: fallback to en
    if (!languageString.contains("en-us")) {
      languageString.add("en-us");
    }
    if (!languageString.contains("en")) {
      languageString.add("en");
    }

    // build a http header for the preferred language
    StringBuilder languages = new StringBuilder();
    float qualifier = 1f;

    for (String line : languageString) {
      if (languages.length() > 0) {
        languages.append(",");
      }
      languages.append(line);
      if (qualifier < 1) {
        languages.append(String.format(Locale.US, ";q=%1.1f", qualifier));
      }
      qualifier -= 0.1;
    }

    return languages.toString().toLowerCase();
  }

  private void processMediaArt(MediaMetadata md, MediaArtworkType type, String label, String image) {
    MediaArtwork ma = new MediaArtwork();
    ma.setPreviewUrl(image);
    ma.setProviderId(getProviderInfo().getId());
    ma.setType(type);
    md.addMediaArt(ma);
  }

  private String cleanString(String oldString) {
    if (StringUtils.isEmpty(oldString)) {
      return "";
    }
    // remove non breaking spaces
    String newString = oldString.replace(String.valueOf((char) 160), " ");
    // and trim
    return StringUtils.trim(newString);
  }

  /****************************************************************************
   * local helper classes
   ****************************************************************************/
  private class ImdbWorker implements Callable<Document> {
    private String   url;
    private String   language;
    private String   country;
    private Document doc = null;

    public ImdbWorker(String url, String language, String country) {
      this.url = url;
      this.language = language;
      this.country = country;
    }

    @Override
    public Document call() throws Exception {
      doc = null;
      try {
        CachedUrl cachedUrl = new CachedUrl(url);
        cachedUrl.addHeader("Accept-Language", getAcceptLanguage(language, country));
        doc = Jsoup.parse(cachedUrl.getInputStream(), imdbSite.getCharset().displayName(), "");
      }
      catch (Exception e) {
        LOGGER.debug("tried to fetch imdb movie page " + url, e);

        // clear Cache
        CachedUrl.removeCachedFileForUrl(url);
      }
      return doc;
    }
  }

  private class TmdbWorker implements Callable<MediaMetadata> {
    private String         imdbId;
    private MediaLanguages language;
    private CountryCode    certificationCountry;

    public TmdbWorker(String imdbId, MediaLanguages language, CountryCode certificationCountry) {
      this.imdbId = imdbId;
      this.language = language;
      this.certificationCountry = certificationCountry;
    }

    @Override
    public MediaMetadata call() throws Exception {
      try {
        TmdbMetadataProvider tmdb = new TmdbMetadataProvider();
        MediaScrapeOptions options = new MediaScrapeOptions();
        options.setLanguage(language);
        options.setCountry(certificationCountry);
        options.setImdbId(imdbId);
        return tmdb.getLocalizedContent(options, null);
      }
      catch (Exception e) {
        return null;
      }
    }
  }

  /*
   * Maps scraper Genres to internal TMM genres
   */
  private MediaGenres getTmmGenre(String genre) {
    MediaGenres g = null;
    if (genre.isEmpty()) {
      return g;
    }
    // @formatter:off
    else if (genre.equals("Action"))         { g = MediaGenres.ACTION; }
    else if (genre.equals("Adventure"))      { g = MediaGenres.ADVENTURE; }
    else if (genre.equals("Animation"))      { g = MediaGenres.ANIMATION; }
    else if (genre.equals("Biography"))      { g = MediaGenres.BIOGRAPHY; }
    else if (genre.equals("Comedy"))         { g = MediaGenres.COMEDY; }
    else if (genre.equals("Crime"))          { g = MediaGenres.CRIME; }
    else if (genre.equals("Documentary"))    { g = MediaGenres.DOCUMENTARY; }
    else if (genre.equals("Drama"))          { g = MediaGenres.DRAMA; }
    else if (genre.equals("Family"))         { g = MediaGenres.FAMILY; }
    else if (genre.equals("Fantasy"))        { g = MediaGenres.FANTASY; }
    else if (genre.equals("Film-Noir"))      { g = MediaGenres.FILM_NOIR; }
    else if (genre.equals("Game-Show"))      { g = MediaGenres.GAME_SHOW; }
    else if (genre.equals("History"))        { g = MediaGenres.HISTORY; }
    else if (genre.equals("Horror"))         { g = MediaGenres.HORROR; }
    else if (genre.equals("Music"))          { g = MediaGenres.MUSIC; }
    else if (genre.equals("Musical"))        { g = MediaGenres.MUSICAL; }
    else if (genre.equals("Mystery"))        { g = MediaGenres.MYSTERY; }
    else if (genre.equals("News"))           { g = MediaGenres.NEWS; }
    else if (genre.equals("Reality-TV"))     { g = MediaGenres.REALITY_TV; }
    else if (genre.equals("Romance"))        { g = MediaGenres.ROMANCE; }
    else if (genre.equals("Sci-Fi"))         { g = MediaGenres.SCIENCE_FICTION; }
    else if (genre.equals("Sport"))          { g = MediaGenres.SPORT; }
    else if (genre.equals("Talk-Show"))      { g = MediaGenres.TALK_SHOW; }
    else if (genre.equals("Thriller"))       { g = MediaGenres.THRILLER; }
    else if (genre.equals("War"))            { g = MediaGenres.WAR; }
    else if (genre.equals("Western"))        { g = MediaGenres.WESTERN; }
    // @formatter:on
    if (g == null) {
      g = MediaGenres.getGenre(genre);
    }
    return g;
  }
}
