package edu.eci.arsw.blueprints.persistence;

import edu.eci.arsw.blueprints.model.Blueprint;
import edu.eci.arsw.blueprints.model.Point;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PostgreSQL-backed persistence; activates with profile "postgres".
 */
@Repository
@Profile("postgres")
@Transactional
public class PostgresBlueprintPersistence implements BlueprintPersistence {

    private final JdbcTemplate jdbc;

    public PostgresBlueprintPersistence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveBlueprint(Blueprint bp) throws BlueprintPersistenceException {
        try {
            jdbc.update("INSERT INTO blueprints(author, name) VALUES (?, ?)", bp.getAuthor(), bp.getName());
            if (!bp.getPoints().isEmpty()) {
                List<Object[]> batch = new ArrayList<>();
                for (int i = 0; i < bp.getPoints().size(); i++) {
                    Point p = bp.getPoints().get(i);
                    batch.add(new Object[]{bp.getAuthor(), bp.getName(), i, p.x(), p.y()});
                }
                jdbc.batchUpdate(
                        "INSERT INTO blueprint_points(author, name, idx, x, y) VALUES (?,?,?,?,?)",
                        batch
                );
            }
        } catch (DuplicateKeyException e) {
            throw new BlueprintPersistenceException("Blueprint already exists: " + bp.getAuthor() + "/" + bp.getName());
        } catch (DataAccessException e) {
            throw new BlueprintPersistenceException("Failed to save blueprint: " + e.getMessage());
        }
    }

    @Override
    public Blueprint getBlueprint(String author, String name) throws BlueprintNotFoundException {
        if (!exists(author, name)) {
            throw new BlueprintNotFoundException("Blueprint not found: " + author + "/" + name);
        }
        List<Point> points = loadPoints(author, name);
        return new Blueprint(author, name, points);
    }

    @Override
    public Set<Blueprint> getBlueprintsByAuthor(String author) throws BlueprintNotFoundException {
        List<Blueprint> data = jdbc.query(
                "SELECT author, name FROM blueprints WHERE author = ?",
                (rs, rowNum) -> new Blueprint(rs.getString("author"), rs.getString("name"), loadPoints(rs.getString("author"), rs.getString("name"))),
                author
        );
        if (data.isEmpty()) throw new BlueprintNotFoundException("No blueprints for author: " + author);
        return data.stream().collect(Collectors.toSet());
    }

    @Override
    public Set<Blueprint> getAllBlueprints() {
        List<Blueprint> data = jdbc.query(
                "SELECT author, name FROM blueprints",
                (rs, rowNum) -> new Blueprint(rs.getString("author"), rs.getString("name"), loadPoints(rs.getString("author"), rs.getString("name")))
        );
        return data.stream().collect(Collectors.toSet());
    }

    @Override
    public void addPoint(String author, String name, int x, int y) throws BlueprintNotFoundException {
        if (!exists(author, name)) {
            throw new BlueprintNotFoundException("Blueprint not found: " + author + "/" + name);
        }
        Integer nextIdx = jdbc.queryForObject(
                "SELECT COALESCE(MAX(idx), -1) + 1 FROM blueprint_points WHERE author = ? AND name = ?",
                Integer.class,
                author, name
        );
        jdbc.update("INSERT INTO blueprint_points(author, name, idx, x, y) VALUES (?,?,?,?,?)",
                author, name, nextIdx, x, y);
    }

    private boolean exists(String author, String name) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM blueprints WHERE author = ? AND name = ?",
                Integer.class,
                author, name
        );
        return count != null && count > 0;
    }

    private List<Point> loadPoints(String author, String name) {
        return jdbc.query(
                "SELECT x, y FROM blueprint_points WHERE author = ? AND name = ? ORDER BY idx",
                (rs, rowNum) -> new Point(rs.getInt("x"), rs.getInt("y")),
                author, name
        );
    }
}
