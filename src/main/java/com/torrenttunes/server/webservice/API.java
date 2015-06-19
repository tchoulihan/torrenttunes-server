package com.torrenttunes.server.webservice;

import static com.torrenttunes.server.db.Tables.ALBUM_SEARCH_VIEW;
import static com.torrenttunes.server.db.Tables.ALBUM_VIEW;
import static com.torrenttunes.server.db.Tables.ARTIST;
import static com.torrenttunes.server.db.Tables.ARTIST_SEARCH_VIEW;
import static com.torrenttunes.server.db.Tables.SONG;
import static com.torrenttunes.server.db.Tables.SONG_SEARCH_VIEW;
import static com.torrenttunes.server.db.Tables.SONG_VIEW;
import static spark.Spark.get;
import static spark.Spark.post;

import java.io.File;
import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.codehaus.jackson.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.torrenttunes.server.DataSources;
import com.torrenttunes.server.Tools;
import com.torrenttunes.server.db.Actions;
import com.torrenttunes.server.db.Tables.SongView;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.Tracker;

public class API {

	static final Logger log = LoggerFactory.getLogger(API.class);


	public static void setup(Tracker tracker) {

		post("/torrent_upload", (req, res) -> {

			try {

				// apache commons-fileupload to handle file upload
				DiskFileItemFactory factory = new DiskFileItemFactory();
				factory.setRepository(new File(DataSources.TORRENTS_DIR()));
				ServletFileUpload fileUpload = new ServletFileUpload(factory);



				List<FileItem> items = fileUpload.parseRequest(req.raw());

				// image is the field name that we want to save
				FileItem item = items.stream()
						.filter(e -> "torrent".equals(e.getFieldName()))
						.findFirst().get();
				String fileName = item.getName();



				File torrentFile = new File(DataSources.TORRENTS_DIR(), fileName);
				item.write(torrentFile);


				Tools.announceAndSaveTorrentFileToDB(tracker, torrentFile);

				// a first test

				log.info(fileName);


				return null;

			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} 



		});




		post("/torrent_info_upload", (req, res) -> {

			try {
				log.info(req.body());
				log.info(req.params().toString());

				String json = req.body();

				JsonNode jsonNode = Tools.jsonToNode(json);

				Tools.dbInit();
				Actions.updateSongInfo(jsonNode);
				

				return "Saved info";
			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}
		});
		
		post("/add_play_count/:infoHash", (req, res) -> {

			try {
				Tools.allowAllHeaders(req, res);
				String infoHash = req.params(":infoHash");
				
				Tools.dbInit();
				Actions.addToPlayCount(infoHash);
				
				

				return "Added play count";
			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}
		});
		
		get("/seeder_upload/:infoHash/:seeders", (req, res) -> {

			try {
				Tools.allowAllHeaders(req, res);
				String infoHash = req.params(":infoHash");
				String seeders = req.params(":seeders");
				
				
				log.info("Seeder upload received for infohash: " + infoHash);
				
				Tools.dbInit();
				Actions.updateSeeders(infoHash, seeders);
				
				

				return "Set seeders";
			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}
		});
		
		get("/download_torrent/:infoHash", (req, res) -> {

			try {
				String infoHash = req.params(":infoHash");

				Tools.dbInit();
				
				// get torrent file location from infoHash
				
				String torrentPath = SONG.findFirst("info_hash = ?", infoHash).
						getString("torrent_path");
				
				log.info("torrent downloaded from : " + torrentPath);
				HttpServletResponse raw = res.raw();
				raw.getOutputStream().write(Files.readAllBytes(Paths.get(torrentPath)));
				raw.getOutputStream().flush();
				raw.getOutputStream().close();

				return res.raw();
				
			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}
		});
		
		get("/download_torrent_info/:infoHash", (req, res) -> {

			try {
				String infoHash = req.params(":infoHash");
				Tools.dbInit();
				SongView sv = SONG_VIEW.findFirst("info_hash = ?", infoHash);
				String json = sv.toJson(false);
	
				
				// Reannounce the torrent:
				// get the torrent
//				String torrentFile = sv.getString("torrent_path");
//				TrackedTorrent tt = TrackedTorrent.load(new File(torrentFile));
//				tracker.announce(tt);
				
				log.info("torrent json: " + json);
				return json;

			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}
		});
		

		get("/get_songs", (req, res) -> {

			try {

				Tools.dbInit();
				String json = SONG.findAll().toJson(false);
	

				return json;

			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}
		});



		get("/song_search/:query", (req, res) -> {

			try {
				Tools.logRequestInfo(req);
				Tools.allowAllHeaders(req, res);
				Tools.dbInit();

				String query = req.params(":query");

				String json = null;

				String queryStr = constructQueryString(query, "search");
				log.info(queryStr);

				json = SONG_SEARCH_VIEW.find(queryStr.toString()).limit(5).toJson(false);

				return json;

			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}


		});

		get("/artist_search/:query", (req, res) -> {

			try {
				Tools.logRequestInfo(req);
				Tools.allowAllHeaders(req, res);
				Tools.dbInit();

				String query = req.params(":query");

				String json = null;

				String queryStr = constructQueryString(query, "search");
				log.info(queryStr);

				json = ARTIST_SEARCH_VIEW.find(queryStr.toString()).limit(5).toJson(false);

				return json;

			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}


		});

		get("/album_search/:query", (req, res) -> {

			try {
				Tools.logRequestInfo(req);
				Tools.allowAllHeaders(req, res);
				Tools.dbInit();

				String query = req.params(":query");

				String json = null;

				String queryStr = constructQueryString(query, "search");
				log.info(queryStr);

				json = ALBUM_SEARCH_VIEW.find(queryStr.toString()).limit(5).toJson(false);

				return json;

			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}


		});

		get("/get_top_albums/:artistMbid", (req, res) -> {

			try {
				Tools.logRequestInfo(req);
				Tools.allowAllHeaders(req, res);
				Tools.dbInit();

				String artistMbid = req.params(":artistMbid");

				String json = null;

				json = ALBUM_VIEW.find("artist_mbid = ?", artistMbid).
						orderBy("plays desc").limit(5).toJson(false);

				return json;

			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}


		});

		get("/get_top_songs/:artistMbid", (req, res) -> {

			try {
				Tools.logRequestInfo(req);
				Tools.allowAllHeaders(req, res);
				Tools.dbInit();

				String artistMbid = req.params(":artistMbid");

				String json = null;

				json = SONG_VIEW.find("artist_mbid = ?", artistMbid).
						orderBy("plays desc").limit(25).toJson(false);

				return json;

			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}


		});

		get("/get_all_albums/:artistMbid", (req, res) -> {

			try {
				Tools.logRequestInfo(req);
				Tools.allowAllHeaders(req, res);
				Tools.dbInit();

				String artistMbid = req.params(":artistMbid");

				String json = null;

				json = ALBUM_VIEW.find("artist_mbid = ?", artistMbid).
						orderBy("year desc").toJson(false);

				return json;

			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}


		});

		get("/get_all_songs/:artistMbid", (req, res) -> {

			try {
				Tools.logRequestInfo(req);
				Tools.allowAllHeaders(req, res);
				Tools.dbInit();

				String artistMbid = req.params(":artistMbid");

				String json = null;

				json = SONG_VIEW.find("artist_mbid = ?", artistMbid).
						orderBy("plays desc").toJson(false);

				return json;

			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}


		});

		get("/get_artist/:artistMbid", (req, res) -> {

			try {
				Tools.logRequestInfo(req);
				Tools.allowAllHeaders(req, res);
				Tools.dbInit();

				String artistMbid = req.params(":artistMbid");

				String json = null;
				json = ARTIST.findFirst("mbid = ?", artistMbid).toJson(false);

				return json;

			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}


		});

		get("/get_album/:albumMbid", (req, res) -> {

			try {
				Tools.logRequestInfo(req);
				Tools.allowAllHeaders(req, res);
				Tools.dbInit();

				String albumMbid = req.params(":albumMbid");

				String json = null;
				json = ALBUM_VIEW.findFirst("mbid = ?", albumMbid).toJson(false);

				return json;

			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}


		});

		get("/get_album_songs/:albumMbid", (req, res) -> {

			try {
				Tools.logRequestInfo(req);
				Tools.allowAllHeaders(req, res);
				Tools.dbInit();

				String albumMbid = req.params(":albumMbid");

				String json = null;
				json = SONG_VIEW.find("release_mbid = ?", albumMbid).
						orderBy("track_number asc").toJson(false);

				return json;

			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}


		});
		
		get("/get_artists", (req, res) -> {

			try {
				Tools.logRequestInfo(req);
				Tools.allowAllHeaders(req, res);
				Tools.dbInit();


				String json = null;
				json = ARTIST.findAll().orderBy("name asc").toJson(false);

				return json;

			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}


		});
		
		get("/get_trending_albums", (req, res) -> {

			try {
				Tools.logRequestInfo(req);
				Tools.allowAllHeaders(req, res);
				Tools.dbInit();


				String json = null;
				json = ALBUM_VIEW.findAll().orderBy("plays desc").limit(4).toJson(false);

				return json;

			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}


		});
		
		
		get("/get_trending_songs", (req, res) -> {

			try {
				Tools.logRequestInfo(req);
				Tools.allowAllHeaders(req, res);
				Tools.dbInit();


				String json = null;
				json = SONG_VIEW.findAll().orderBy("plays desc").limit(40).toJson(false);

				return json;

			} catch (Exception e) {
				res.status(666);
				e.printStackTrace();
				return e.getMessage();
			} finally {
				Tools.dbClose();
			}


		});


	}

	public static String constructQueryString(String query, String columnName) {

		try {
			query = java.net.URLDecoder.decode(query, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		String[] splitWords = query.split(" ");
		StringBuilder queryStr = new StringBuilder();
		for(int i = 0;;) {
			String word = columnName + " like '%" + splitWords[i++] + "%'";
			queryStr.append(word);

			if (i < splitWords.length) {
				queryStr.append(" and ");
			} else {
				break;
			}
		}

		return queryStr.toString();

	}

}