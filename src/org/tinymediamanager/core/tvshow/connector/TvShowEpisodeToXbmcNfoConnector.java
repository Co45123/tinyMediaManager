///*
// * Copyright 2012 - 2013 Manuel Laggner
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package org.tinymediamanager.core.tvshow.connector;
//
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileNotFoundException;
//import java.io.IOException;
//import java.io.InputStreamReader;
//import java.io.Reader;
//import java.io.StringWriter;
//import java.io.Writer;
//import java.util.ArrayList;
//import java.util.List;
//
//import javax.xml.bind.JAXBContext;
//import javax.xml.bind.JAXBException;
//import javax.xml.bind.Marshaller;
//import javax.xml.bind.Unmarshaller;
//import javax.xml.bind.annotation.XmlAnyElement;
//import javax.xml.bind.annotation.XmlElement;
//import javax.xml.bind.annotation.XmlRootElement;
//import javax.xml.bind.annotation.XmlType;
//
//import org.apache.commons.io.FileUtils;
//import org.apache.commons.lang3.StringUtils;
//import org.apache.commons.lang3.SystemUtils;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.tinymediamanager.core.tvshow.TvShow;
//import org.tinymediamanager.core.tvshow.TvShowActor;
//import org.tinymediamanager.scraper.MediaGenres;
//
///**
// * The Class TvShowEpisodeToXbmcNfoConnector.
// * 
// * @author Manuel Laggner
// */
//@XmlRootElement(name = "tvshow")
//@XmlType(propOrder = { "title", "rating", "votes", "plot", "id", "actors" })
//public class TvShowEpisodeToXbmcNfoConnector {
//
//  /** The Constant logger. */
//  private static final Logger LOGGER = LoggerFactory.getLogger(TvShowEpisodeToXbmcNfoConnector.class);
//
//  /** The season. */
//  private String              season;
//
//  /** The episode. */
//  private String              episode;
//
//  /** The title. */
//  private String              title;
//
//  /** The rating. */
//  private float               rating;
//
//  /** The votes. */
//  private int                 votes;
//
//  /** The plot. */
//  private String              plot;
//
//  /** The thumb. */
//  private String              thumb;
//
//  /** The actors. */
//  @XmlAnyElement(lax = true)
//  private List<Object>        actors;
//
//  /**
//   * Instantiates a new tv show episode to xbmc nfo connector.
//   */
//  public TvShowEpisodeToXbmcNfoConnector() {
//    actors = new ArrayList<Object>();
//  }
//
//  /**
//   * Sets the data.
//   * 
//   * @param tvShow
//   *          the tv show
//   * @return the string
//   */
//  public static String setData(TvShow tvShow) {
//    TvShowEpisodeToXbmcNfoConnector xbmc = null;
//    JAXBContext context = null;
//    String nfoFilename = "tvshow.nfo";
//    File nfoFile = new File(tvShow.getPath(), nfoFilename);
//
//    // load existing NFO if possible
//    if (nfoFile.exists()) {
//      try {
//        synchronized (JAXBContext.class) {
//          context = JAXBContext.newInstance(TvShowEpisodeToXbmcNfoConnector.class, Actor.class);
//        }
//        Unmarshaller um = context.createUnmarshaller();
//        Reader in = new InputStreamReader(new FileInputStream(nfoFile), "UTF-8");
//        xbmc = (TvShowEpisodeToXbmcNfoConnector) um.unmarshal(in);
//      }
//      catch (Exception e) {
//        LOGGER.error("failed to parse " + nfoFilename, e);
//      }
//    }
//
//    // create new
//    if (xbmc == null) {
//      xbmc = new TvShowEpisodeToXbmcNfoConnector();
//    }
//
//    // set data
//    xbmc.setId(tvShow.getId("tvdb").toString());
//    xbmc.setTitle(tvShow.getTitle());
//    xbmc.setRating(tvShow.getRating());
//    xbmc.setVotes(tvShow.getVotes());
//    xbmc.setPlot(tvShow.getPlot());
//    xbmc.setYear(tvShow.getYear());
//
//    xbmc.genres.clear();
//    for (MediaGenres genre : tvShow.getGenres()) {
//      xbmc.addGenre(genre.toString());
//    }
//
//    xbmc.actors.clear();
//    for (TvShowActor actor : tvShow.getActors()) {
//      xbmc.addActor(actor.getName(), actor.getCharacter(), actor.getThumb());
//    }
//
//    // and marshall it
//    try {
//
//      synchronized (JAXBContext.class) {
//        context = JAXBContext.newInstance(TvShowEpisodeToXbmcNfoConnector.class, Actor.class);
//      }
//      Marshaller m = context.createMarshaller();
//      m.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
//      m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
//
//      Writer w = new StringWriter();
//      m.marshal(xbmc, w);
//      StringBuilder sb = new StringBuilder(w.toString());
//      w.close();
//
//      // on windows make windows conform linebreaks
//      if (SystemUtils.IS_OS_WINDOWS) {
//        sb = new StringBuilder(sb.toString().replaceAll("(?<!\r)\n", "\r\n"));
//      }
//      FileUtils.write(nfoFile, sb, "UTF-8");
//    }
//    catch (JAXBException e) {
//      LOGGER.error("setData", e);
//    }
//    catch (IOException e) {
//      LOGGER.error("setData", e);
//    }
//
//    // return only the name w/o path
//    return nfoFilename;
//  }
//
//  /**
//   * Gets the data.
//   * 
//   * @param nfoFilename
//   *          the nfo filename
//   * @return the data
//   */
//  public static TvShow getData(String nfoFilename) {
//    // try to parse XML
//    JAXBContext context;
//    TvShow tvShow = null;
//    try {
//      synchronized (JAXBContext.class) {
//        context = JAXBContext.newInstance(TvShowEpisodeToXbmcNfoConnector.class, Actor.class);
//      }
//      Unmarshaller um = context.createUnmarshaller();
//      Reader in = new InputStreamReader(new FileInputStream(nfoFilename), "UTF-8");
//      TvShowEpisodeToXbmcNfoConnector xbmc = (TvShowEpisodeToXbmcNfoConnector) um.unmarshal(in);
//      tvShow = new TvShow();
//      if (StringUtils.isNotBlank(xbmc.getId())) {
//        tvShow.setId("tvdb", xbmc.getId());
//      }
//      tvShow.setTitle(xbmc.getTitle());
//      tvShow.setRating(xbmc.getRating());
//      tvShow.setVotes(xbmc.getVotes());
//      tvShow.setYear(xbmc.getYear());
//      tvShow.setPlot(xbmc.getPlot());
//
//      for (String genre : xbmc.getGenres()) {
//        String[] genres = genre.split("/");
//        for (String g : genres) {
//          MediaGenres genreFound = MediaGenres.getGenre(g.trim());
//          if (genreFound != null) {
//            tvShow.addGenre(genreFound);
//          }
//        }
//      }
//
//      for (Actor actor : xbmc.getActors()) {
//        TvShowActor tvShowActor = new TvShowActor(actor.getName(), actor.getRole());
//        tvShowActor.setThumb(actor.getThumb());
//        tvShow.addActor(tvShowActor);
//      }
//    }
//    catch (FileNotFoundException e) {
//      LOGGER.error("setData", e);
//      return null;
//    }
//
//    catch (Exception e) {
//      LOGGER.error("setData", e);
//      return null;
//    }
//
//    // only return if a movie name has been found
//    if (StringUtils.isEmpty(tvShow.getTitle())) {
//      return null;
//    }
//    return tvShow;
//  }
//
//  /**
//   * Gets the title.
//   * 
//   * @return the title
//   */
//  @XmlElement(name = "title")
//  public String getTitle() {
//    return title;
//  }
//
//  /**
//   * Sets the title.
//   * 
//   * @param title
//   *          the new title
//   */
//  public void setTitle(String title) {
//    this.title = title;
//  }
//
//  /**
//   * Gets the rating.
//   * 
//   * @return the rating
//   */
//  @XmlElement(name = "rating")
//  public float getRating() {
//    return rating;
//  }
//
//  /**
//   * Gets the plot.
//   * 
//   * @return the plot
//   */
//  @XmlElement(name = "plot")
//  public String getPlot() {
//    return plot;
//  }
//
//  /**
//   * Sets the rating.
//   * 
//   * @param rating
//   *          the new rating
//   */
//  public void setRating(float rating) {
//    this.rating = rating;
//  }
//
//  /**
//   * Gets the votes.
//   * 
//   * @return the votes
//   */
//  @XmlElement(name = "votes")
//  public int getVotes() {
//    return votes;
//  }
//
//  /**
//   * Sets the votes.
//   * 
//   * @param votes
//   *          the new votes
//   */
//  public void setVotes(int votes) {
//    this.votes = votes;
//  }
//
//  /**
//   * Gets the year.
//   * 
//   * @return the year
//   */
//  @XmlElement(name = "year")
//  public String getYear() {
//    return year;
//  }
//
//  /**
//   * Sets the year.
//   * 
//   * @param year
//   *          the new year
//   */
//  public void setYear(String year) {
//    this.year = year;
//  }
//
//  /**
//   * Sets the plot.
//   * 
//   * @param plot
//   *          the new plot
//   */
//  public void setPlot(String plot) {
//    this.plot = plot;
//  }
//
//  /**
//   * Adds the genre.
//   * 
//   * @param genre
//   *          the genre
//   */
//  public void addGenre(String genre) {
//    genres.add(genre);
//  }
//
//  /**
//   * Gets the genres.
//   * 
//   * @return the genres
//   */
//  public List<String> getGenres() {
//    return this.genres;
//  }
//
//  /**
//   * Gets the id.
//   * 
//   * @return the id
//   */
//  @XmlElement(name = "id")
//  public String getId() {
//    return id;
//  }
//
//  /**
//   * Sets the id.
//   * 
//   * @param id
//   *          the new id
//   */
//  public void setId(String id) {
//    this.id = id;
//  }
//
//  /**
//   * Adds the actor.
//   * 
//   * @param name
//   *          the name
//   * @param role
//   *          the role
//   * @param thumb
//   *          the thumb
//   */
//  public void addActor(String name, String role, String thumb) {
//    Actor actor = new Actor(name, role, thumb);
//    actors.add(actor);
//  }
//
//  /**
//   * Gets the actors.
//   * 
//   * @return the actors
//   */
//  public List<Actor> getActors() {
//    // @XmlAnyElement(lax = true) causes all unsupported tags to be in actors;
//    // filter Actors out
//    List<Actor> pureActors = new ArrayList<Actor>();
//    for (Object obj : actors) {
//      if (obj instanceof Actor) {
//        Actor actor = (Actor) obj;
//        pureActors.add(actor);
//      }
//    }
//    return pureActors;
//  }
//
//  // inner class actor to represent actors
//  /**
//   * The Class Actor.
//   * 
//   * @author Manuel Laggner
//   */
//  @XmlRootElement(name = "actor")
//  public static class Actor {
//
//    /** The name. */
//    private String name;
//
//    /** The role. */
//    private String role;
//
//    /** The thumb. */
//    private String thumb;
//
//    /**
//     * Instantiates a new actor.
//     */
//    public Actor() {
//    }
//
//    /**
//     * Instantiates a new actor.
//     * 
//     * @param name
//     *          the name
//     * @param role
//     *          the role
//     * @param thumb
//     *          the thumb
//     */
//    public Actor(String name, String role, String thumb) {
//      this.name = name;
//      this.role = role;
//      this.thumb = thumb;
//    }
//
//    /**
//     * Gets the name.
//     * 
//     * @return the name
//     */
//    @XmlElement(name = "name")
//    public String getName() {
//      return name;
//    }
//
//    /**
//     * Sets the name.
//     * 
//     * @param name
//     *          the new name
//     */
//    public void setName(String name) {
//      this.name = name;
//    }
//
//    /**
//     * Gets the role.
//     * 
//     * @return the role
//     */
//    @XmlElement(name = "role")
//    public String getRole() {
//      return role;
//    }
//
//    /**
//     * Sets the role.
//     * 
//     * @param role
//     *          the new role
//     */
//    public void setRole(String role) {
//      this.role = role;
//    }
//
//    /**
//     * Gets the thumb.
//     * 
//     * @return the thumb
//     */
//    @XmlElement(name = "thumb")
//    public String getThumb() {
//      return thumb;
//    }
//
//    /**
//     * Sets the thumb.
//     * 
//     * @param thumb
//     *          the new thumb
//     */
//    public void setThumb(String thumb) {
//      this.thumb = thumb;
//    }
//
//  }
// }