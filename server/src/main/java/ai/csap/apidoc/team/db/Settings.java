package ai.csap.apidoc.team.db;

import org.springframework.stereotype.Component;

/** {@code csap_team_setting} 与 {@code csap_team_cursor} 的键值读写。 */
@Component
public class Settings {

    private final Db db;

    public Settings(Db db) {
        this.db = db;
    }

    public String get(String name) {
        return db.one("SELECT setting_value FROM csap_team_setting WHERE name = ?", (rs, i) -> rs.getString(1), name);
    }

    public void set(String name, String value) {
        if (db.update("UPDATE csap_team_setting SET setting_value = ? WHERE name = ?", value, name) == 0) {
            try {
                db.update("INSERT INTO csap_team_setting (name, setting_value) VALUES (?, ?)", name, value);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                db.update("UPDATE csap_team_setting SET setting_value = ? WHERE name = ?", value, name);
            }
        }
    }

    public void remove(String name) {
        db.update("DELETE FROM csap_team_setting WHERE name = ?", name);
    }

    public Long cursor(String name) {
        String v = db.one("SELECT cursor_value FROM csap_team_cursor WHERE name = ?", (rs, i) -> rs.getString(1), name);
        return v == null ? null : Long.parseLong(v);
    }

    public void setCursor(String name, long value) {
        if (db.update("UPDATE csap_team_cursor SET cursor_value = ? WHERE name = ?", String.valueOf(value), name) == 0) {
            try {
                db.update("INSERT INTO csap_team_cursor (name, cursor_value) VALUES (?, ?)", name, String.valueOf(value));
            } catch (org.springframework.dao.DuplicateKeyException e) {
                db.update("UPDATE csap_team_cursor SET cursor_value = ? WHERE name = ?", String.valueOf(value), name);
            }
        }
    }
}
