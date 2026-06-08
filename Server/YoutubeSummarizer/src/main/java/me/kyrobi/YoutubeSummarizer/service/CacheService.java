package me.kyrobi.YoutubeSummarizer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.swing.text.html.Option;
import java.sql.*;
import java.util.Optional;

@Service
public class CacheService {

    private final String dbPath;

    public CacheService(){
        dbPath = System.getProperty("user.dir") + "/cache.db";

        try(Connection conn = connect()){

            Statement statement = conn.createStatement();
            statement.execute("""
                CREATE TABLE IF NOT EXISTS summary_cache (
                    video_id TEXT NOT NULL,
                    size TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    PRIMARY KEY (video_id, size)
                )
            """);

        } catch(SQLException e){
            throw new RuntimeException("Failed to initialize cache DB", e);
        }
    }

    public Optional<String> getSummary(String videoID, String size){
        try(Connection conn = connect()){

            PreparedStatement ps = conn.prepareStatement(
                    "SELECT summary FROM summary_cache WHERE video_id = ? AND size = ?"
            );

            ps.setString(1, videoID);
            ps.setString(2, size);
            ResultSet rs = ps.executeQuery();

            if(rs.next()){
                return Optional.of(rs.getString("summary"));
            }

        } catch(SQLException e){
            System.out.println("[CACHE] Read error: " + e.getMessage());
            throw new RuntimeException("Failed to initialize cache DB", e);
        }
        return Optional.empty();
    }


    public void saveSummary(String videoID, String size, String summary){

        try(Connection conn = connect()){

            PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO summary_cache (video_id, size, summary) VALUES (?, ?, ?)"
            );

            ps.setString(1, videoID);
            ps.setString(2, size);
            ps.setString(3, summary);
            ps.executeUpdate();
        } catch (SQLException e){
            System.out.println("[CACHE] Write error: " + e.getMessage());
            throw new RuntimeException("Failed to initialize cache DB", e);
        }

    }


    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

}
