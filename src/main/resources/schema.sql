CREATE TABLE IF NOT EXISTS blueprints (
    author VARCHAR(100) NOT NULL,
    name   VARCHAR(100) NOT NULL,
    CONSTRAINT blueprints_pk PRIMARY KEY (author, name)
);

CREATE TABLE IF NOT EXISTS blueprint_points (
    author VARCHAR(100) NOT NULL,
    name   VARCHAR(100) NOT NULL,
    idx    INT NOT NULL,
    x      INT NOT NULL,
    y      INT NOT NULL,
    CONSTRAINT blueprint_points_pk PRIMARY KEY (author, name, idx),
    CONSTRAINT blueprint_points_bp_fk FOREIGN KEY (author, name)
        REFERENCES blueprints(author, name)
        ON DELETE CASCADE
);
