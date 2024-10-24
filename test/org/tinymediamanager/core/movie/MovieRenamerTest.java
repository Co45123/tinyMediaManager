package org.tinymediamanager.core.movie;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.TmmModuleManager;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.entities.MediaFileSubtitle;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;

public class MovieRenamerTest {
  private final static Logger LOGGER = LoggerFactory.getLogger(MovieRenamerTest.class);

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    TmmModuleManager.getInstance().startUp();
    MovieModuleManager.getInstance().startUp();
    TvShowModuleManager.getInstance().startUp();
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    TvShowModuleManager.getInstance().shutDown();
    MovieModuleManager.getInstance().shutDown();
    TmmModuleManager.getInstance().shutDown();
  }

  @Test
  public void special() {
    System.out.println(MovieRenamer.replaceInvalidCharacters("jb: the bla"));
    System.out.println(MovieRenamer.replaceInvalidCharacters("jb : the bla"));
    System.out.println(MovieRenamer.replaceInvalidCharacters("2:22"));
    System.out.println(MovieRenamer.replaceInvalidCharacters("2 :22"));
  }

  @Test
  public void checkPathConsistency() {
    // get all movie paths
    // check whether path contains another
    //
    // ds/file1.mkv <- MultiMovieDir
    // ds/file2.mkv <- MultiMovieDir
    // ds/A/file3.mkv <- single, but since we have another movie deeper, this MUST be a MultiMovieDir
    // ds/A/title/file5.mkv single

  }

  @Test
  public void checkDiff() {
    try {
      TmmModuleManager.getInstance().startUp();
      MovieModuleManager.getInstance().startUp();
      MovieList instance = MovieList.getInstance();
      // -------------------------------------------------------------------------------------

      LOGGER.debug("path expression: " + MovieModuleManager.MOVIE_SETTINGS.getMovieRenamerPathname());
      LOGGER.debug("file expression: " + MovieModuleManager.MOVIE_SETTINGS.getMovieRenamerFilename());

      for (Movie movie : instance.getMovies()) {
        System.out.println(movie.getTitle());
        List<MediaFile> oldFiles = new ArrayList<MediaFile>();
        Set<MediaFile> newFiles = new LinkedHashSet<MediaFile>();
        boolean renameFolder = false;

        String newVideoFileName = "";

        // VIDEO needs to be renamed first, since all others depend on that name!!!
        for (MediaFile mf : movie.getMediaFiles(MediaFileType.VIDEO)) {
          oldFiles.add(new MediaFile(mf));
          MediaFile ftr = generateFilename(movie, mf, newVideoFileName).get(0); // there can be only one
          newFiles.add(ftr);
          if (newVideoFileName.isEmpty()) {
            // so remember first renamed video file basename (w/o stacking or extension)
            newVideoFileName = Utils.cleanStackingMarkers(ftr.getBasename());
          }
        }

        // all the other MFs...
        for (MediaFile mf : movie.getMediaFilesExceptType(MediaFileType.VIDEO)) {
          oldFiles.add(new MediaFile(mf));
          newFiles.addAll(generateFilename(movie, mf, newVideoFileName)); // N:M
        }

        // movie folder needs a rename?
        File oldMovieFolder = new File(movie.getPath());
        String newPathname = MovieRenamer.createDestinationForFoldername(MovieModuleManager.MOVIE_SETTINGS.getMovieRenamerPathname(), movie);
        File newMovieFolder = new File(movie.getDataSource() + File.separator + newPathname);
        if (!oldMovieFolder.equals(newMovieFolder)) {
          renameFolder = true;
          if (!movie.isMultiMovieDir()) {
            System.out.println("rename FOLDER " + oldMovieFolder + " -> " + newMovieFolder);
          }
          else {
            System.out.println(" ... is a MultiMovieDir; create FOLDER " + newMovieFolder);
          }
          // update already the "old" files with new path, so we can simply do a contains check ;)
          for (MediaFile omf : oldFiles) {
            omf.replacePathForRenamedFolder(oldMovieFolder, newMovieFolder);
          }
        }

        // change status of MFs, if they have been added or not
        System.out.println("=============== NEW");
        for (MediaFile mf : newFiles) {
          if (!oldFiles.contains(mf)) {
            System.out.println(mf.getFilename() + "  -  ADDED");
          }
          else {
            System.out.println(mf.getFilename() + "  -  SAME");
          }
        }
        System.out.println("=============== OLD");
        for (MediaFile mf : oldFiles) {
          if (!newFiles.contains(mf)) {
            System.out.println(mf.getFilename() + "  -  REMOVED");
          }
          else {
            System.out.println(mf.getFilename() + "  -  SAME");
          }
        }
        System.out.println("==================");

      } // end movie loop

      // -------------------------------------------------------------------------------------
      MovieModuleManager.getInstance().shutDown();
      TmmModuleManager.getInstance().shutDown();
    }
    catch (Exception e) {
      System.out.println(e.getMessage());
    }
  }

  /**
   * generates renamed filename(s) per MF
   * 
   * @param movie
   *          the movie (for datasource, path)
   * @param mf
   *          the MF
   * @param videoFileName
   *          the basename of the renamed videoFileName (saved earlier)
   * @return list of renamed filename
   */
  public ArrayList<MediaFile> generateFilename(Movie movie, MediaFile mf, String videoFileName) {
    // return list of all generated MFs
    ArrayList<MediaFile> newFiles = new ArrayList<MediaFile>();

    String newPathname = MovieRenamer.createDestinationForFoldername(MovieModuleManager.MOVIE_SETTINGS.getMovieRenamerPathname(), movie);
    String movieDir = movie.getDataSource() + File.separatorChar + newPathname + File.separatorChar;

    String newFilename = videoFileName;
    if (newFilename == null || newFilename.isEmpty()) {
      newFilename = MovieRenamer.createDestinationForFilename(MovieModuleManager.MOVIE_SETTINGS.getMovieRenamerFilename(), movie);
    }

    switch (mf.getType()) {
      case VIDEO:
        MediaFile vid = new MediaFile(mf);
        if (movie.isDisc() || mf.isDiscFile()) {
          // just replace new path and return file (do not change names!)
          vid.replacePathForRenamedFolder(new File(movie.getPath()), new File(movieDir));
        }
        else {
          newFilename += getStackingString(mf);
          newFilename += "." + mf.getExtension();
          vid.setFile(new File(movieDir + newFilename));
        }
        newFiles.add(vid);
        break;

      case TRAILER:
        MediaFile trail = new MediaFile(mf);
        newFilename += "-trailer." + mf.getExtension();
        trail.setFile(new File(movieDir + newFilename));
        newFiles.add(trail);
        break;

      case SUBTITLE:
        String lang = "";
        String forced = "";
        List<MediaFileSubtitle> mfsl = mf.getSubtitles();

        if (mfsl != null && mfsl.size() > 0) {
          MediaFileSubtitle mfs = mfsl.get(0);
          lang = mfs.getLanguage();
          if (mfs.isForced()) {
            forced = ".forced";
          }
        }
        newFilename += getStackingString(mf);
        newFilename += forced;
        if (!lang.isEmpty()) {
          newFilename += "." + lang;
        }
        newFilename += "." + mf.getExtension();
        MediaFile sub = new MediaFile(mf);
        sub.setFile(new File(movieDir + newFilename));
        newFiles.add(sub);
        break;

      case NFO:
        List<MovieNfoNaming> nfonames = new ArrayList<MovieNfoNaming>();
        if (movie.isMultiMovieDir()) {
          // Fixate the name regardless of setting
          nfonames.add(MovieNfoNaming.FILENAME_NFO);
        }
        else {
          nfonames = MovieModuleManager.MOVIE_SETTINGS.getMovieNfoFilenames();
        }
        for (MovieNfoNaming name : nfonames) {
          newFilename = movie.getNfoFilename(name, videoFileName);
          if (newFilename.isEmpty()) {
            continue;
          }
          MediaFile nfo = new MediaFile(mf);
          nfo.setFile(new File(movieDir + newFilename));
          newFiles.add(nfo);
        }
        break;

      case POSTER:
        List<MoviePosterNaming> posternames = new ArrayList<MoviePosterNaming>();
        if (movie.isMultiMovieDir()) {
          // Fixate the name regardless of setting
          posternames.add(MoviePosterNaming.FILENAME_POSTER_JPG);
          posternames.add(MoviePosterNaming.FILENAME_POSTER_PNG);
        }
        else {
          posternames = MovieModuleManager.MOVIE_SETTINGS.getMoviePosterFilenames();
        }
        for (MoviePosterNaming name : posternames) {
          newFilename = MovieArtworkHelper.getPosterFilename(name, movie, videoFileName);
          if (newFilename != null && !newFilename.isEmpty()) {
            String curExt = mf.getExtension();
            if (curExt.equalsIgnoreCase("tbn")) {
              String cont = mf.getContainerFormat();
              if (cont.equalsIgnoreCase("PNG")) {
                curExt = "png";
              }
              else if (cont.equalsIgnoreCase("JPEG")) {
                curExt = "jpg";
              }
            }
            if (!curExt.equals(FilenameUtils.getExtension(newFilename))) {
              // match extension to not rename PNG to JPG and vice versa
              continue;
            }
          }
          MediaFile pos = new MediaFile(mf);
          pos.setFile(new File(movieDir + newFilename));
          newFiles.add(pos);
        }
        break;

      case FANART:
        List<MovieFanartNaming> fanartnames = new ArrayList<MovieFanartNaming>();
        if (movie.isMultiMovieDir()) {
          // Fixate the name regardless of setting
          fanartnames.add(MovieFanartNaming.FILENAME_FANART_JPG);
          fanartnames.add(MovieFanartNaming.FILENAME_FANART_PNG);
        }
        else {
          fanartnames = MovieModuleManager.MOVIE_SETTINGS.getMovieFanartFilenames();
        }
        for (MovieFanartNaming name : fanartnames) {
          newFilename = MovieArtworkHelper.getFanartFilename(name, movie, videoFileName);
          if (newFilename != null && !newFilename.isEmpty()) {
            String curExt = mf.getExtension();
            if (curExt.equalsIgnoreCase("tbn")) {
              String cont = mf.getContainerFormat();
              if (cont.equalsIgnoreCase("PNG")) {
                curExt = "png";
              }
              else if (cont.equalsIgnoreCase("JPEG")) {
                curExt = "jpg";
              }
            }
            if (!curExt.equals(FilenameUtils.getExtension(newFilename))) {
              // match extension to not rename PNG to JPG and vice versa
              continue;
            }
          }
          MediaFile fan = new MediaFile(mf);
          fan.setFile(new File(movieDir + newFilename));
          newFiles.add(fan);
        }
        break;

      default:
        // return 1:1, only with renamed path
        MediaFile def = new MediaFile(mf);
        def.replacePathForRenamedFolder(new File(movie.getPath()), new File(movieDir));
        newFiles.add(def);
        break;
    }

    return newFiles;
  }

  /**
   * returns "delimiter + stackingString" for use in filename
   * 
   * @param mf
   *          a mediaFile
   * @return eg ".CD1" dependent of settings
   */
  private String getStackingString(MediaFile mf) {
    String stacking = Utils.getStackingMarker(mf.getFilename());
    String delimiter = " ";
    if (MovieModuleManager.MOVIE_SETTINGS.isMovieRenamerSpaceSubstitution()) {
      delimiter = MovieModuleManager.MOVIE_SETTINGS.getMovieRenamerSpaceReplacement();
    }
    if (!stacking.isEmpty()) {
      return delimiter + stacking;
    }
    else if (mf.getStacking() != 0) {
      return delimiter + "CD" + mf.getStacking();
    }
    return "";
  }

  @Test
  public void testPattern() {
    testAll(getRegexForPattern("$T($Y)"));
    testAll(getRegexForPattern("$T.($Y)"));
    testAll(getRegexForPattern("$T ($$$$)"));
  }

  private void testAll(String regex) {
    testRegex(regex, "this is the title (2012)");
    testRegex(regex, "this.is.the.scene.title (2012)");
    testRegex(regex, "this_is_another_title (2012)");
    testRegex(regex, "this is the title(2012)");
    testRegex(regex, "blabla [r12] (2012)");
    System.out.println();
  }

  private void testRegex(String regex, String text) {
    System.out.println("Test:  " + text + " -> " + text.matches(regex));
  }

  public String getRegexForPattern(String p) {
    Pattern SPECIAL_REGEX_CHARS = Pattern.compile("[{}()\\[\\].+*?^\\\\|]");
    System.out.println("Start: " + p);

    p = SPECIAL_REGEX_CHARS.matcher(p).replaceAll("\\\\$0");
    System.out.println("RegEx: " + p);

    p = p.replaceAll("\\$T", "([\\\\s\\\\w\\\\.]+)"); // alphanum+_ & whitespaces & dots
    p = p.replaceAll("\\$Y", "(\\\\d{4})");
    p = p.replaceAll("\\$1", "(\\\\w)");
    System.out.println("Patrn: " + p);

    return p;
  }
}
