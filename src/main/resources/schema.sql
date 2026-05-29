CREATE TABLE IF NOT EXISTS auth_users (
    id VARCHAR(64) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_sessions (
    access_token VARCHAR(255) PRIMARY KEY,
    refresh_token VARCHAR(255) NOT NULL UNIQUE,
    user_id VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    refresh_expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS profiles (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(30) NOT NULL DEFAULT '',
    handle VARCHAR(31) NOT NULL DEFAULT '',
    email VARCHAR(255) NOT NULL DEFAULT '',
    bio VARCHAR(50) NOT NULL DEFAULT '',
    current_streak INT NOT NULL DEFAULT 0,
    best_streak INT NOT NULL DEFAULT 0,
    total_checks INT NOT NULL DEFAULT 0,
    trophies INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pods (
    id VARCHAR(120) PRIMARY KEY,
    name VARCHAR(40) NOT NULL,
    description VARCHAR(100) NOT NULL,
    member_count INT NOT NULL DEFAULT 0,
    certified_today INT NOT NULL DEFAULT 0,
    max_members INT NOT NULL,
    streak INT NOT NULL DEFAULT 0,
    tag_line VARCHAR(100) NOT NULL,
    needs_check_in BOOLEAN NOT NULL DEFAULT TRUE,
    invite_code VARCHAR(120) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS pod_tags (
    pod_id VARCHAR(120) NOT NULL,
    tag VARCHAR(40) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (pod_id, tag),
    CONSTRAINT fk_pod_tags_pod FOREIGN KEY (pod_id) REFERENCES pods(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS pod_members (
    pod_id VARCHAR(120) NOT NULL,
    profile_id VARCHAR(64) NOT NULL,
    member_role VARCHAR(20) NOT NULL DEFAULT '멤버',
    streak INT NOT NULL DEFAULT 0,
    checked_in_today BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (pod_id, profile_id),
    CONSTRAINT fk_pod_members_pod FOREIGN KEY (pod_id) REFERENCES pods(id) ON DELETE CASCADE,
    CONSTRAINT fk_pod_members_profile FOREIGN KEY (profile_id) REFERENCES profiles(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS check_ins (
    id VARCHAR(120) PRIMARY KEY,
    pod_id VARCHAR(120) NOT NULL,
    author_id VARCHAR(64) NOT NULL,
    meta VARCHAR(120) NOT NULL DEFAULT '',
    text VARCHAR(60) NOT NULL,
    media_url VARCHAR(500),
    likes INT NOT NULL DEFAULT 0,
    comments INT NOT NULL DEFAULT 0,
    checked_by_me BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_check_ins_pod FOREIGN KEY (pod_id) REFERENCES pods(id) ON DELETE CASCADE,
    CONSTRAINT fk_check_ins_author FOREIGN KEY (author_id) REFERENCES profiles(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_stats (
    profile_id VARCHAR(64) NOT NULL,
    stat_month INT NOT NULL,
    stat_year INT NOT NULL,
    current_streak INT NOT NULL DEFAULT 0,
    best_streak INT NOT NULL DEFAULT 0,
    weekly_checks INT NOT NULL DEFAULT 0,
    weekly_goal INT NOT NULL DEFAULT 7,
    total_checks INT NOT NULL DEFAULT 0,
    active_pods INT NOT NULL DEFAULT 0,
    monthly_completion_rate INT NOT NULL DEFAULT 0,
    checked_days_in_month INT NOT NULL DEFAULT 0,
    heatmap TEXT,
    recent_trophy VARCHAR(120),
    PRIMARY KEY (profile_id, stat_year, stat_month),
    CONSTRAINT fk_user_stats_profile FOREIGN KEY (profile_id) REFERENCES profiles(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS notifications (
    id VARCHAR(120) PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    body VARCHAR(255) NOT NULL,
    meta VARCHAR(120) NOT NULL DEFAULT '',
    notification_type VARCHAR(40) NOT NULL DEFAULT 'all',
    urgent BOOLEAN NOT NULL DEFAULT FALSE,
    is_read BOOLEAN NOT NULL DEFAULT FALSE
);
