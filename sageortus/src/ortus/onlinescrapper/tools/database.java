/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ortus.onlinescrapper.tools;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang.StringEscapeUtils;
import ortus.onlinescrapper.MediaObject;
import ortus.onlinescrapper.themoviedb.Movie;
import ortus.onlinescrapper.themoviedb.SearchResult;
import ortus.onlinescrapper.thetvdb.Actor;
import ortus.onlinescrapper.thetvdb.Episode;
import ortus.onlinescrapper.thetvdb.Series;
import sagex.api.AiringAPI;
import sagex.api.MediaFileAPI;
import sagex.api.ShowAPI;

/**
 *
 * @author jphipps
 */
public class database extends ortus.vars {
    public static void LogFind(int scantype, int mediaid, String searchtitle, HashMap<String,SearchResult> resultTitles) {
        ortus.api.executeSQL("delete from sage.scrapperlog where scantype = " + scantype + " and mediaid = " + mediaid);
         Object[] mk = resultTitles.keySet().toArray();
        for ( Object mt : mk ) {
            ortus.api.executeSQL("insert into sage.scrapperlog (scantype, mediaid, searchtitle, foundtitle, foundkey, scandate) values( " + scantype + "," + mediaid + ",'" + StringEscapeUtils.escapeSql(searchtitle) + "','" + StringEscapeUtils.escapeSql((String)mt) + "','" + StringEscapeUtils.escapeSql(resultTitles.get(mt).getMetadatakey()) + "',current_timestamp)");
        }

        if ( mk.length == 0) {
            ortus.api.executeSQL("insert into sage.scrapperlog (scantype, mediaid, searchtitle, foundtitle, foundkey, scandate) values( " + scantype + "," + mediaid + ",'" + StringEscapeUtils.escapeSql(searchtitle) + "','None Found','None Found',current_timestamp)");
        }
    }

    public static Movie GetCacheMetadataIMDB(String imdbid) {
        return GetCacheMetadata("imdbid",imdbid);
    }

    public static Movie GetCacheMetadataTMDB(String tmdbid) {
        return GetCacheMetadata("tmdbid",tmdbid);
    }

    private static Movie GetCacheMetadata(String keytype, String key) {
//        ortus.api.DebugLog(LogLevel.Trace2,"GetCacheMetadata: Searching cache for type: " + keytype + " key: " + key.trim());
        Connection conn = ortus.api.GetConnection();
        String sql = "select mediaobject from sage.metadatacache where " + keytype + " = ?";
        try {
          Movie cacheitem = null;
          PreparedStatement stmt = conn.prepareStatement(sql);
          stmt.setString(1,key.trim());
          ResultSet rs = stmt.executeQuery();
          if  ( rs.next() ) {
              cacheitem = (Movie)rs.getObject(1);
          }
          stmt.close();
	  conn.close();
          if ( cacheitem != null )
              ortus.api.DebugLog(LogLevel.Trace2," GetMetadataCache: Found: key: " + key + " in cache");
          return cacheitem;
        } catch(Exception e ) {
	    try { conn.close(); } catch(Exception ex) {}
            ortus.api.DebugLog(LogLevel.Error,"GetCacheMetadata: SQL: " + sql);
            ortus.api.DebugLog( LogLevel.Error, "GetCacheMetadata: SQLException: " , e);
            return null;
        }
    }
    public static void cacheMetadata(Movie mo) {
//	ortus.api.DebugLog(LogLevel.Trace2,"cacheMetadata:  caching tmdbid: " + mo.GetId() + " imdbid: " + mo.GetIMDBId());
        Connection conn = ortus.api.GetConnection();

        List<Object> result = ortus.api.executeSQLQuery("select imdbid from sage.metadatacache where imdbid = '" + mo.getImdbid() + "' and tmdbid = '" + mo.getTmdbid() + "'");
        if ( result.size() > 0) {
            return;
        }
        String sql = "insert into sage.metadatacache ( tmdbid, imdbid, title, mediaobject ) values(?,?,?,?)";
        try {
          PreparedStatement stmt = conn.prepareStatement(sql);
          stmt.setString(1,mo.getTmdbid());
          stmt.setString(2,mo.getImdbid());
          stmt.setString(3,mo.getName());
          stmt.setObject(4, mo);
          stmt.execute();
          stmt.close();
	  conn.close();
        } catch(Exception e ) {
	    try { conn.close(); } catch(Exception ex) {}
            ortus.api.DebugLog(LogLevel.Error,"cacheMetadata: SQL: " + sql);
            ortus.api.DebugLog( LogLevel.Error, "cacheMetadata: SQLException: " , e);
            return;
        }
        return;
    }

    public static void WriteMediatoDB(MediaObject mo) {

       String name = StringEscapeUtils.escapeSql(mo.getShowtitle());
       int mfid = MediaFileAPI.GetMediaFileID(mo.getMedia());

       String runtime = String.valueOf(MediaFileAPI.GetFileDuration(mo.getMedia()));
       if ( runtime.isEmpty())
           runtime="0";

       String SQL ="UPDATE sage.media SET mediatitle='"+ name +"', episodetitle = '" + StringEscapeUtils.escapeSql(ShowAPI.GetShowEpisode(mo.getMedia())) + "', mediapath='" + StringEscapeUtils.escapeSql(MediaFileAPI.GetFileForSegment(mo.getMedia(),0).getAbsolutePath()) + "', mediaencoding = '" + MediaFileAPI.GetMediaFileFormatDescription(mo.getMedia()).trim() + "', mediasize = " + MediaFileAPI.GetSize(mo.getMedia()) + ", mediaduration = " + runtime + " , lastwatchedtime = " +
                AiringAPI.GetLatestWatchedTime(mo.getMedia()) + " , airingstarttime = " + AiringAPI.GetAiringStartTime(mo.getMedia()) + ", mediatype = " + mo.getMediaTypeInt() + ", mediagroup = " + mo.getMediaGroupInt() + "  WHERE mediaid = " + String.valueOf(mfid);
        int success = ortus.api.executeSQL(SQL);
        if(success < 1){
            SQL =  "INSERT INTO sage.media (mediaid, mediatitle, episodetitle, mediapath, mediaencoding, mediatype, mediagroup, mediasize, mediaduration, lastwatchedtime, airingstarttime, mediaimporttime) " +
                       " VALUES(" + mfid + ", '" + name + "','" + StringEscapeUtils.escapeSql(ShowAPI.GetShowEpisode(mo.getMedia())) + "','" + StringEscapeUtils.escapeSql(MediaFileAPI.GetFileForSegment(mo.getMedia(),0).getAbsolutePath()) + "','" + MediaFileAPI.GetMediaFileFormatDescription(mo.getMedia()).trim() + "'," + mo.getMediaTypeInt() + ", " + mo.getMediaGroupInt() + ", " + MediaFileAPI.GetSize(mo.getMedia()) +
                       ", " + MediaFileAPI.GetFileDuration(mo.getMedia()) + ", " + AiringAPI.GetLatestWatchedTime(mo.getMedia()) + "," + AiringAPI.GetAiringStartTime(mo.getMedia()) + ", current_timestamp)";
            ortus.api.executeSQL(SQL);
        }
    }

    public static void WriteEpisodetoDB(Episode Episode,String SeriesTitle){
        String Description =  StringEscapeUtils.escapeSql(Episode.getOverview());
        String EpisodeName = StringEscapeUtils.escapeSql(Episode.getEpisodeName());
        float workrating = 0;
        try { workrating = Float.parseFloat(Episode.getRating()); } catch(Exception e) {}
        if ( workrating == 0) workrating = 5;

        String SQL ="UPDATE sage.episode SET title ='" +EpisodeName +"',description ='" +
                Description +"',originalairdate=";
	if ( Episode.getFirstAired().isEmpty() || Episode.getFirstAired().equals("0000-00-00"))
		SQL+="'1900-01-01'";
	else
		SQL+="'" + Episode.getFirstAired()+"'";
	SQL+=",userrating='" +Episode.getRating() + "',seasonno='" + Episode.getSeasonNumber() +"',episodeno =" +Episode.getEpisodeNumber() +
                " WHERE seriesid = " + Episode.getSeriesId() + " and seasonid = " + Episode.getSeasonId() + " and episodeid = " + Episode.getId();
        int success = ortus.api.executeSQL(SQL);
        if(success < 1){
            SQL =  "INSERT INTO sage.episode (seriesid, episodeid, seasonid, episodeno,  title,description,originalairdate,userrating,seasonno )  " +
                    " VALUES(" + Episode.getSeriesId() + "," + Episode.getId() + ", "+ Episode.getSeasonId() + " , " + Episode.getEpisodeNumber() + ", '" + EpisodeName +"','" +
                    Description +"',";
	    if ( Episode.getFirstAired().isEmpty())
			SQL+="'1900-01-01'";
	    else
		    SQL+="'" + Episode.getFirstAired() +"'";
	    SQL+="," + workrating + "," + Episode.getSeasonNumber() + ")";
	    ortus.api.executeSQL(SQL);
        }

        List<String> directors = Episode.getDirectors();
        for ( String x : directors) {
            ortus.api.executeSQL("insert into sage.seriescast ( seriesid, episodeid, name, job, character) values ( " + Episode.getSeriesId() + "," + Episode.getId() + ",'" + x.replaceAll("'","''") + "','Director','')");
        }
        List<String> writers = Episode.getWriters();
        for ( String x : writers) {
            ortus.api.executeSQL("insert into sage.seriescast ( seriesid, episodeid, name, job, character) values ( " + Episode.getSeriesId() + "," + Episode.getId() + ",'" + x.replaceAll("'","''") + "','Writer','')");
        }
        List<String> guests = Episode.getGuestStars();
        for ( String x : guests) {
            ortus.api.executeSQL("insert into sage.seriescast ( seriesid, episodeid, name, job, character) values ( " + Episode.getSeriesId() + "," + Episode.getId() + ",'" + x.replaceAll("'","''") + "','Guest Star','')");
        }

    }

    public static void UpdateEpisodeDB(Episode Episode){
        String Description =  StringEscapeUtils.escapeSql(Episode.getOverview());
        String EpisodeName = StringEscapeUtils.escapeSql(Episode.getEpisodeName());
        float workrating = 0;
        try { workrating = Float.parseFloat(Episode.getRating()); } catch(Exception e) {}
        if ( workrating == 0) workrating = 5;

        String SQL ="UPDATE sage.episode SET title ='" +EpisodeName +"',description ='" +
                Description +"',originalairdate=";
	if ( Episode.getFirstAired().isEmpty() || Episode.getFirstAired().equals("0000-00-00"))
		SQL+="'1900-01-01'";
	else
		SQL+="'" + Episode.getFirstAired()+"'";
	SQL+=",userrating=" +workrating + ",seasonno='" + Episode.getSeasonNumber() +"',episodeno =" +Episode.getEpisodeNumber() +
                " WHERE seriesid = " + Episode.getSeriesId() + " and seasonid = " + Episode.getSeasonId() + " and episodeid = " + Episode.getId();
        int success = ortus.api.executeSQL(SQL);

	if ( success < 1) {
		List<Object> result = ortus.api.executeSQLQuery("select seriesid from sage.series where seriesid = " + Episode.getSeriesId());

		if ( result.size() < 1) {
			ortus.api.DebugLog(LogLevel.Info, "UpdateEpisodeDB: Episodeid: " + Episode.getId() + " not found");
			return;
		}
	}
    
	if ( success < 1) {
	    ortus.api.DebugLog(LogLevel.Info, "UpdateEpisodeDB: Inserted new Episodeid: " + Episode.getId());

            SQL =  "INSERT INTO sage.episode (seriesid, episodeid, seasonid, episodeno,  title,description,originalairdate,userrating,seasonno )  " +
                    " VALUES(" + Episode.getSeriesId() + "," + Episode.getId() + ", "+ Episode.getSeasonId() + " , " + Episode.getEpisodeNumber() + ", '" + EpisodeName +"','" +
                    Description +"',";
	    if ( Episode.getFirstAired().isEmpty() || Episode.getFirstAired().equals("0000-00-00"))
			SQL+="'1900-01-01'";
	    else
		    SQL+="'" + Episode.getFirstAired() +"'";
	    SQL+="," + workrating + "," + Episode.getSeasonNumber() + ")";
	    ortus.api.executeSQL(SQL);
        } else {
		ortus.api.DebugLog(LogLevel.Info, "UpdateEpisodeDB: Updated Episodeid: " + Episode.getId());
	}

	success = ortus.api.executeSQL("delete from sage.seriescast where seriesid = " + Episode.getSeriesId() + " and episodeid = " + Episode.getId());
        List<String> directors = Episode.getDirectors();
        for ( String x : directors) {
            ortus.api.executeSQL("insert into sage.seriescast ( seriesid, episodeid, name, job, character) values ( " + Episode.getSeriesId() + "," + Episode.getId() + ",'" + StringEscapeUtils.escapeSql(x) + "','Director','')");
        }
        List<String> writers = Episode.getWriters();
        for ( String x : writers) {
            ortus.api.executeSQL("insert into sage.seriescast ( seriesid, episodeid, name, job, character) values ( " + Episode.getSeriesId() + "," + Episode.getId() + ",'" + StringEscapeUtils.escapeSql(x) + "','Writer','')");
        }
        List<String> guests = Episode.getGuestStars();
        for ( String x : guests) {
            ortus.api.executeSQL("insert into sage.seriescast ( seriesid, episodeid, name, job, character) values ( " + Episode.getSeriesId() + "," + Episode.getId() + ",'" + StringEscapeUtils.escapeSql(x) + "','Guest Star','')");
        }

    }
    public static void WriteSeriestoDB(Series Series, List<Actor> actors) {
        //get 's out of description
        String Description =  StringEscapeUtils.escapeSql(Series.getOverview());
        // get ' our of episode title
        String Name = StringEscapeUtils.escapeSql(Series.getSeriesName());
        float workrating = 0;
        try { workrating = Float.parseFloat(Series.getRating()); } catch(Exception e) {}
        if ( workrating == 0) workrating = 5;
        String runtime = Series.getRuntime();
        if ( runtime.isEmpty())
            runtime="0";
        String workfirstair = Series.getFirstAired();
        if( workfirstair.isEmpty() || workfirstair.equals("0000-00-00"))
            workfirstair="1900-01-01";

        String SQL ="UPDATE sage.series SET title ='" +Name +"',firstair='" +
                workfirstair +"',airday='" + Series.getAirsDayOfWeek() +"',status='" +Series.getStatus() +
                "',description='" + Description +"',network='" + StringEscapeUtils.escapeSql(Series.getNetwork()) +"',userrating=" + workrating +
                ",mpaarated='" + Series.getContentRating() +"',runtime=" + runtime+", imdbid = '" + Series.getImdbId() + "', zap2itid = '" + Series.getZap2ItId() + "', airtime = '" + Series.getAirsTime() + "' WHERE seriesid=" +Series.getId();
        int success = ortus.api.executeSQL(SQL);
        if(success < 1){
            SQL =  "INSERT INTO sage.series (seriesid,title,firstair,airday,status,description,network,userrating,mpaarated,runtime, imdbid, zap2itid, airtime) " +
                    "VALUES("+ Series.getId() +",'" +Name +"','" + workfirstair +"','" + Series.getAirsDayOfWeek() +"','" +Series.getStatus() +
                    "','" + Description +"','" + StringEscapeUtils.escapeSql(Series.getNetwork()) +"'," + workrating +
                    ",'" + Series.getContentRating() +"'," + runtime+", '" + Series.getImdbId() + "','" + Series.getZap2ItId() + "','" + Series.getAirsTime() + "')";
            ortus.api.executeSQL(SQL);
        }
        ortus.api.executeSQL("delete from sage.seriesgenre where seriesid = " + Series.getId());
        for ( String g : Series.getGenres()) {
            ortus.api.executeSQL("insert into sage.seriesgenre ( seriesid, name ) values ( " + Series.getId() + ",'" + g + "')");
        }
        ortus.api.executeSQL("delete from sage.seriescast where seriesid = " + Series.getId());
        for ( Actor a : actors) {
            ortus.api.executeSQL("insert into sage.seriescast ( seriesid, episodeid, personid, name, job, character) values ( " + Series.getId() + ", 0,"+ a.getId() +",'" + a.getName().replaceAll("'","''") + "','actor','" + a.getRole().replaceAll("'","''") + "')");
        }
    }

    public static void UpdateSeriesDB(Series Series) {
        //get 's out of description
        String Description =  StringEscapeUtils.escapeSql(Series.getOverview());
        // get ' our of episode title
        String Name = StringEscapeUtils.escapeSql(Series.getSeriesName());
        float workrating = 0;
        try { workrating = Float.parseFloat(Series.getRating()); } catch(Exception e) {}
        if ( workrating == 0) workrating = 5;
        String runtime = Series.getRuntime();
        if ( runtime.isEmpty())
            runtime="0";
        String workfirstair = Series.getFirstAired();
        if( workfirstair.isEmpty())
            workfirstair="1900-01-01";

        String SQL ="UPDATE sage.series SET title ='" +Name +"',firstair='" +
                workfirstair +"',airday='" + Series.getAirsDayOfWeek() +"',status='" +Series.getStatus() +
                "',description='" + Description +"',network='" +Series.getNetwork() +"',userrating=" + workrating +
                ",mpaarated='" + Series.getContentRating() +"',runtime=" + runtime+", imdbid = '" + Series.getImdbId() + "', zap2itid = '" + Series.getZap2ItId() + "', airtime = '" + Series.getAirsTime() + "' WHERE seriesid=" +Series.getId();
        int success = ortus.api.executeSQL(SQL);
	if ( success > 0)
		ortus.api.DebugLog(LogLevel.Info, "UpdateSeriesDB: Updated seriesid: " + Series.getId() + " title: " + Series.getSeriesName());
	else
		ortus.api.DebugLog(LogLevel.Trace2,"UpdateSeriesDB: Seriesid: " + Series.getId() + " not found");
    }

    public static void WriteTVFanart(String metadataid, String type, String url , String filename) {
        int width = 0;
        int height = 0;
        int imagetype = 0;
        HashMap<String,String> imginfo = ortus.image.util.GetImageInfo(ortus.api.GetFanartFolder() + java.io.File.separator + filename);

	if ( imginfo != null) {
		width = Integer.parseInt(imginfo.get("width"));
		height = Integer.parseInt(imginfo.get("height"));

		if ( height < 200)
			imagetype = 1;
		if ( height < 600)
			imagetype = 2;
		if ( height > 599)
			imagetype = 3;

		ortus.api.DebugLog(LogLevel.Trace,"FanartImage: ImageType: " + imagetype + " Size: Width: " + width + " Height: " + height);
	}

	String SQL = "update sage.fanart set width = " + width + ", height="+height + ",imagetype = " + imagetype + ",file = '" + filename + "' where metadataid = '" + metadataid + "' and type = '" + type + "' and url = '" + url + "'";
	int success = ortus.api.executeSQL(SQL);
	if ( success < 1) {
		SQL =  "INSERT INTO sage.fanart (width, height, imagetype, metadataid, type, url, file) VALUES("+ width + "," + height + "," + imagetype + ",'" + metadataid + "', '" + type + "','" + url + "','" + filename + "')";
		ortus.api.executeSQL(SQL);
	}
    }
    
    public static boolean UpdateEpisodeMediaID(MediaObject mo, Series series) {
        ortus.api.DebugLog(LogLevel.Trace2,"UpdateEpisodeMediaID: running");
        boolean titleset = true;
//        Pattern pattern = Pattern.compile("S(\\d+)E(\\d+)");
//        Matcher matcher = pattern.matcher(mo.getEpisodetitle());
//        int EpisodeNo = 0;
//        int SeasonNo = 0;
        List<List> mtv;
        String workSeriesTitle = StringEscapeUtils.escapeSql(mo.getShowtitle());
        String workEpisodeTitle = StringEscapeUtils.escapeSql(mo.getEpisodetitle());

        if ( ! mo.getSeasonno().isEmpty()) {
//            ortus.api.DebugLog(LogLevel.Trace2, " UpdateEpisodeID: matcher found: " + matcher.groupCount());
            ortus.api.DebugLog(LogLevel.Trace2, " Series found, title: " + mo.getShowtitle() + " Season:" +  mo.getSeasonno() + " Episode: " + mo.getEpisodeno());
            titleset = false;
//            SeasonNo = Integer.parseInt(matcher.group(1));
//            EpisodeNo = Integer.parseInt(matcher.group(2));
            String sql = "select e.seasonno, s.seriesid, e.episodeno, e.title, e.description from sage.episode e, sage.series s where s.seriesid = e.seriesid and lower(s.title) = '" + workSeriesTitle.toLowerCase().trim() + "' and e.seasonno = " + mo.getSeasonno() + " and e.episodeno = " + mo.getEpisodeno();
            mtv = ortus.api.executeSQLQueryArray(sql);
        } else {
            mtv = ortus.api.executeSQLQueryArray("select e.seasonno, s.seriesid, e.episodeno, e.title, e.description from sage.episode e, sage.series s where s.seriesid = e.seriesid and episodeid not = 999 and lower(s.title) = '" + workSeriesTitle.toLowerCase() + "' and lower(e.title) = '" + workEpisodeTitle.toLowerCase() + "'");
        }

        if ( mtv.size() < 1) {
		ortus.api.DebugLog(LogLevel.Trace2, " Series: " + mo.getShowtitle() + " Episode: " + mo.getEpisodetitle() + " not found" );
		if ( workEpisodeTitle.isEmpty())
			workEpisodeTitle = workSeriesTitle;
		String SQL = "delete from sage.episode where seriesid = " + series.getId() + " and  episodeid = 999 and mediaid = " + MediaFileAPI.GetMediaFileID(mo.getMedia());
		ortus.api.executeSQL(SQL);
		SQL =  "INSERT INTO sage.episode (seriesid, episodeid, seasonid, episodeno, mediaid, title,description,originalairdate,userrating,seasonno )  " +
		    " VALUES(" + series.getId() + ",999,0,999," + MediaFileAPI.GetMediaFileID(mo.getMedia()) + ",'" + StringEscapeUtils.escapeSql(workEpisodeTitle) +"','" + StringEscapeUtils.escapeSql(ShowAPI.GetShowDescription(mo.getMedia())) + "',";
		if ( ShowAPI.GetOriginalAiringDate(mo.getMedia()) == 0 )
			SQL+="'1900-01-01'";
		else {
			String date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date (ShowAPI.GetOriginalAiringDate(mo.getMedia())));
                        if ( date.equals("0000-00-00"))
                            date="1900-01-01";
			SQL+="'" + date +"'";
		}
		SQL+=",5,999)";
		ortus.api.DebugLog(LogLevel.Trace2," Adding dummy series: " + series.getId() + " with SQL: " + SQL);
		int success = ortus.api.executeSQL(SQL);
		if ( success > 0 ) {
//		    SQL = "update sage.media set mediatype = 3, mediatitle = '" + mo.getShowtitle() + "',episodetitle = '" + ShowAPI.GetShowEpisode(mo.getMedia()).replaceAll("'","''") + "' where mediaid = " + MediaFileAPI.GetMediaFileID(mo.getMedia());
//		    success = ortus.api.executeSQL(SQL);
//		    if ( success > 0 )
			ortus.api.DebugLog(LogLevel.Trace2, "UpdateEpisodeMediaID: Successful");
//		    else
//			ortus.api.DebugLog(LogLevel.Trace2, "UpdateEpisodeMediaID: Failed media series set");
		} else
			ortus.api.DebugLog(LogLevel.Trace2, "UpdateEpisodeMediaID: Failed media series set");

        } else {
		String SQL ="UPDATE sage.episode SET mediaid = " + MediaFileAPI.GetMediaFileID(mo.getMedia()) + " where seasonno = " + mtv.get(0).get(0) + " and seriesid = " + mtv.get(0).get(1) + " and episodeno = " + mtv.get(0).get(2);
		int success = ortus.api.executeSQL(SQL);
		if ( success > 0 ) {
//		    SQL = "update sage.media set mediatype = 3, mediatitle = '" + workSeriesTitle + "',episodetitle = '" + ((String)mtv.get(0).get(3)).replaceAll("'","''") + "' where mediaid = " + MediaFileAPI.GetMediaFileID(mo.getMedia());
//		    success = ortus.api.executeSQL(SQL);
//		    if ( success > 0 )
			mo.setEpisodetitle((String)mtv.get(0).get(3));
                        mo.setOverview((String)mtv.get(0).get(4));
			ortus.api.DebugLog(LogLevel.Trace2, "UpdateEpisodeMediaID: Successful");
//		    else
//			ortus.api.DebugLog(LogLevel.Trace2, "UpdateEpisodeMediaID: Failed media series set");
		} else
		    ortus.api.DebugLog(LogLevel.Trace2, "UpdateEpisodeMediaID: Failed");
	}
	return true;
    }

//    public boolean WritePictureDB(Object mediafile) {
//
//        if ( name.isEmpty())
//            name = MediaFileAPI.GetMediaTitle(mediafile);
//        overview = overview.replaceAll("'","''");
//        name = name.replaceAll("'","''");
//	name = name.replaceAll("\"","");
//	episodetitle = episodetitle.replaceAll("'","''");
//	episodetitle = episodetitle.replaceAll("\"","");
//        int mfid = MediaFileAPI.GetMediaFileID(mediafile);
//
//        String SQL ="UPDATE sage.media SET mediatitle='"+ name +"', episodetitle = '" + episodetitle + "', mediatype=" + mediatype + ",mediagroup=" + mediagroup + ", mediapath='" + MediaFileAPI.GetFileForSegment(mediafile,0).getAbsolutePath().replaceAll("'","''") + "', mediaencoding = '" + MediaFileAPI.GetMediaFileFormatDescription(mediafile).trim() + "', mediasize = " + MediaFileAPI.GetSize(mediafile) + ", mediaduration = " + MediaFileAPI.GetFileDuration(mediafile) + " , lastwatchedtime = " +
//                AiringAPI.GetLatestWatchedTime(mediafile) + ", airingstarttime = " + AiringAPI.GetAiringStartTime(mediafile) + " WHERE mediaid = " + String.valueOf(mfid);
//        int success = ortus.api.executeSQL(SQL);
//        if(success < 1){
//            SQL =  "INSERT INTO sage.media (mediaid, mediatitle,mediapath, mediaencoding, mediatype, mediagroup, mediasize, mediaduration, lastwatchedtime, airingstarttime, mediaimporttime) " +
//                       " VALUES(" + mfid + ", '" + name + "','" + MediaFileAPI.GetFileForSegment(mediafile,0).getAbsolutePath().replaceAll("'","''") + "','" + MediaFileAPI.GetMediaFileFormatDescription(mediafile).trim() + "'," + mediatype + "," + mediagroup + ", " + MediaFileAPI.GetSize(mediafile) +
//                       ", " + MediaFileAPI.GetFileDuration(mediafile) + ", " + AiringAPI.GetLatestWatchedTime(mediafile) + "," + AiringAPI.GetAiringStartTime(mediafile) + ", current_timestamp)";
//            ortus.api.executeSQL(SQL);
//        }
//    } 
}