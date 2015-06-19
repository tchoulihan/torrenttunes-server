package com.torrenttunes.server.db;

import static com.torrenttunes.server.db.Tables.*;

import java.io.File;
import java.util.NoSuchElementException;

import org.codehaus.jackson.JsonNode;
import org.javalite.activejdbc.DBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.musicbrainz.mp3.tagger.Tools.Song.MusicBrainzRecordingQuery;
import com.torrenttunes.server.Tools;
import com.torrenttunes.server.db.Tables.Song;
import com.turn.ttorrent.tracker.TrackedTorrent;

public class Actions {

	static final Logger log = LoggerFactory.getLogger(Actions.class);


	public static void saveTorrentToDB(File torrentFile, TrackedTorrent t) {

		try {

			log.info("Saving " + torrentFile.getName() + " to the DB");


			// First get the MBID from the filename
			String mbid = torrentFile.getName().split("_")[0];
			
			// Save the song
			Song song = SONG.create("torrent_path", torrentFile.getAbsolutePath(),
					"info_hash", t.getHexInfoHash().toLowerCase(),
					"mbid", mbid,
					"seeders", t.seeders());

			Boolean success = song.saveIt();

		} catch(DBException e) {		
			if (e.getMessage().contains("[SQLITE_CONSTRAINT]")) {
				log.error("Not adding " + torrentFile.getName() + ", Song was already in the DB");
			} else {
				e.printStackTrace();
			}
		}

	}


	public static void updateSongInfo(JsonNode jsonNode) {

		// Get the variables
		String songMbid = jsonNode.get("mbid").asText();
		String title = jsonNode.get("title").asText();
		String artist = jsonNode.get("artist").asText();
		String artistMbid = jsonNode.get("artist_mbid").asText();
		String album = jsonNode.get("album").asText();
		String albumMbid = jsonNode.get("album_mbid").asText();
		Long durationMS = jsonNode.get("duration_ms").asLong();
		Integer trackNumber = jsonNode.get("track_number").asInt();
		String year = jsonNode.get("year").asText();
		String albumArtUrl = jsonNode.get("album_coverart_url").asText();
		if (albumArtUrl.equals("null")) {
			albumArtUrl = null;
		}
		String largeThumbnail = jsonNode.get("album_coverart_thumbnail_large").asText();
		if (largeThumbnail.equals("null")) {
			largeThumbnail = null;
		}
		String smallThumbnail = jsonNode.get("album_coverart_thumbnail_small").asText();
		if (smallThumbnail.equals("null")) {
			smallThumbnail = null;
		}
		
		// First, check to see if the album or artist need to be created:
		Artist artistRow = ARTIST.findFirst("mbid = ?", artistMbid);
		if (artistRow == null) {
			
			// Fetch some links and images from musicbrainz
			com.musicbrainz.mp3.tagger.Tools.Artist mbInfo = 
					com.musicbrainz.mp3.tagger.Tools.Artist.fetchArtist(artistMbid);
			
			String imageURL = null;
		
				if (mbInfo.getImage() != null && mbInfo.getImage().contains("commons.wikimedia.org")) {
					imageURL = Tools.convertWikimediaCommonsURLToImageUrl(mbInfo.getImage());
				}
			

			artistRow = ARTIST.createIt("mbid", artistMbid,
					"name", artist,
					"image_url", imageURL,
					"wikipedia_link", mbInfo.getWikipedia(),
					"allmusic_link", mbInfo.getAllMusic(),
					"official_homepage", mbInfo.getOfficialHomepage(),
					"imdb", mbInfo.getIMDB(),
					"lyrics", mbInfo.getLyrics(),
					"youtube", mbInfo.getYoutube(),
					"soundcloud", mbInfo.getSoundCloud(),
					"lastfm", mbInfo.getLastFM());
		}
		
		// Do the same for album
		Release releaseRow = RELEASE.findFirst("mbid = ?" , albumMbid);
		if (releaseRow == null) {
			releaseRow = RELEASE.createIt("mbid", albumMbid,
					"title", album,
					"artist_mbid", artistMbid,
					"year", year,
					"album_coverart_url", albumArtUrl,
					"album_coverart_thumbnail_large", largeThumbnail,
					"album_coverart_thumbnail_small", smallThumbnail);		
		}
		
		
		
		// Find it by the MBID
		Song song = SONG.findFirst("mbid = ?", songMbid);

		song.set("title", title,
				"release_mbid", albumMbid,
				"duration_ms", durationMS,
				"track_number", trackNumber);
		song.saveIt();


	}


	public static void addToPlayCount(String infoHash) {
		SONG.update("plays = plays + ?", "info_hash = ?", 1, infoHash);
		
	}


	public static void updateSeeders(String infoHash, String seeders) {
		Song song = SONG.findFirst("info_hash = ?", infoHash);
		song.set("seeders", seeders);
		song.saveIt();
		
	}

}