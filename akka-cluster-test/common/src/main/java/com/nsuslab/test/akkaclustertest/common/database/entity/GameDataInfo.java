package com.nsuslab.test.akkaclustertest.common.database.entity;

import javax.persistence.*;

@Entity
@NamedQueries(value = {
        @NamedQuery(name = "GameDataInfo.findAll", query = "SELECT g FROM GameDataInfo g"),
        @NamedQuery(name = "GameDataInfo.findAllKeys", query = "SELECT g.gameId FROM GameDataInfo g")
})
@Table(name = "GAMEDATA_INFO")
public class GameDataInfo {
        @Id
        public long gameId;
        @Column(length = 50)
        public String gameName;
        @Column(length = 50)
        public String gameType;
        @Column
        public int playerCount;
        @Column
        public int maxCount;

        public GameDataInfo() {
                this.gameId = 0L;
                this.gameName = null;
                this.gameType = null;
                this.playerCount = 0;
                this.maxCount = 0;
        }

        public long getGameId() {
                return gameId;
        }

        public void setGameId(long gameId) {
                this.gameId = gameId;
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

        public int getPlayerCount() {
                return playerCount;
        }

        public void setPlayerCount(int playerCount) {
                this.playerCount = playerCount;
        }

        public int getMaxCount() {
                return maxCount;
        }

        public void setMaxCount(int maxCount) {
                this.maxCount = maxCount;
        }

        @Override
        public String toString() {
                return "[\n\t"+this.gameId+"\n\t"+this.gameName+"\n\t"+this.gameType+"\n\t"+this.maxCount+"\n\t"+this.playerCount+"\n]";
        }
}
