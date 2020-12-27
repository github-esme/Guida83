/*
 * This file is part of Guida.
 * Copyright (C) 2020 Guida
 *
 * Guida is a fork of the OdinMS MapleStory Server.
 * The following is the original copyright notice:
 *
 *     This file is part of the OdinMS Maple Story Server
 *     Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
 *                        Matthias Butz <matze@odinms.de>
 *                        Jan Christian Meyer <vimes@odinms.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation. You may not use, modify
 * or distribute this program under any other version of the
 * GNU Affero General Public License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package guida.client.anticheat;

import guida.client.MapleCharacter;

public class CheatingOffenseEntry {

    private final CheatingOffense offense;
    private final MapleCharacter chrfor;
    private final long firstOffense;
    private int count = 0;
    private long lastOffense;
    private String param;
    private int dbid = -1;

    public CheatingOffenseEntry(CheatingOffense offense, MapleCharacter chrfor) {
        super();
        this.offense = offense;
        this.chrfor = chrfor;
        firstOffense = System.currentTimeMillis();
    }

    public CheatingOffense getOffense() {
        return offense;
    }

    public int getCount() {
        return count;
    }

    public MapleCharacter getChrfor() {
        return chrfor;
    }

    public void incrementCount() {
        count++;
        lastOffense = System.currentTimeMillis();
    }

    public boolean isExpired() {
        return lastOffense < System.currentTimeMillis() - offense.getValidityDuration();
    }

    public int getPoints() {
        return count * offense.getPoints();
    }

    public String getParam() {
        return param;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public long getLastOffenseTime() {
        return lastOffense;
    }

    public int getDbId() {
        return dbid;
    }

    public void setDbId(int dbid) {
        this.dbid = dbid;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (chrfor == null ? 0 : chrfor.getId());
        result = prime * result + (offense == null ? 0 : offense.hashCode());
        result = prime * result + Long.valueOf(firstOffense).hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CheatingOffenseEntry other = (CheatingOffenseEntry) obj;
        if (chrfor == null) {
            if (other.chrfor != null) {
                return false;
            }
        } else if (chrfor.getId() != other.chrfor.getId()) {
            return false;
        }
        if (offense == null) {
            if (other.offense != null) {
                return false;
            }
        } else if (!offense.equals(other.offense)) {
            return false;
        }
        return other.firstOffense == firstOffense;
    }
}
