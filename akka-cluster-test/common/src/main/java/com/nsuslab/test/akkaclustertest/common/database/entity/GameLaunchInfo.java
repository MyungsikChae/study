package com.nsuslab.test.akkaclustertest.common.database.entity;

import javax.persistence.*;

@Entity
@NamedQueries(value = {
        @NamedQuery(name = "GameLaunchInfo.findAll", query = "SELECT g FROM GameLaunchInfo g"),
        @NamedQuery(name = "GameLaunchInfo.findAllKeys", query = "SELECT g.timestamp FROM GameLaunchInfo g")
})
@Table(name = "GAMELAUNCH_INFO")
public class GameLaunchInfo {
        @Id
        public Long timestamp;
        @Column(length = 50)
        public String gameName;
        @Column(length = 50)
        public String gameType;
        @Column
        public String location;

        public GameLaunchInfo() {
                this.timestamp = 0L;
                this.gameName = null;
                this.gameType = null;
                this.location = null;
        }

        public GameLaunchInfo(Long timestamp) {
                this.timestamp = timestamp;
                this.gameName = null;
                this.gameType = null;
                this.location = null;
        }

        public Long getTimestamp() {
                return timestamp;
        }

        public void setTimestamp(Long timestamp) {
                this.timestamp = timestamp;
        }

        public String getGameName() {
                return gameName;
        }

        public void setGameName(String gameName) {
                this.gameName = gameName;
        }

        public String getGameType() {
                return gameType;
        }

        public void setGameType(String gameType) {
                this.gameType = gameType;
        }

        public String getLocation() {
                return location;
        }

        public void setLocation(String location) {
                this.location = location;
        }
}
