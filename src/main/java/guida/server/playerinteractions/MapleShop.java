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

package guida.server.playerinteractions;

import guida.client.IItem;
import guida.client.MapleClient;
import guida.client.MapleInventoryType;
import guida.client.MaplePet;
import guida.database.DatabaseConnection;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.tools.MaplePacketCreator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author Matze
 */
public class MapleShop {

    private static final Set<Integer> rechargeableItems = new LinkedHashSet<>();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleShop.class);

    static {
        for (int i = 2070000; i <= 2070018; i++) {
            rechargeableItems.add(i);
        }
        rechargeableItems.remove(2070014); // doesn't exist
        rechargeableItems.remove(2070017);

        for (int i = 2330000; i <= 2330005; i++) {
            rechargeableItems.add(i);
        }
        rechargeableItems.add(2331000);//Blaze Capsule
        rechargeableItems.add(2332000);//Glaze Capsule
    }

    private final int id;
    private final int npcId;
    private final List<MapleShopItem> items;

    /**
     * Creates a new instance of MapleShop
     */
    private MapleShop(int id, int npcId) {
        this.id = id;
        this.npcId = npcId;
        items = new LinkedList<>();
    }

    public static MapleShop createFromDB(int id, boolean isShopId) {
        MapleShop ret = null;
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        int shopId;
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps;
            if (isShopId) {
                ps = con.prepareStatement("SELECT * FROM shops WHERE shopid = ?");
            } else {
                ps = con.prepareStatement("SELECT * FROM shops WHERE npcid = ?");
            }

            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                shopId = rs.getInt("shopid");
                ret = new MapleShop(shopId, rs.getInt("npcid"));
                rs.close();
                ps.close();
            } else {
                rs.close();
                ps.close();
                return null;
            }
            ps = con.prepareStatement("SELECT * FROM shopitems WHERE shopid = ? ORDER BY position ASC");
            ps.setInt(1, shopId);
            rs = ps.executeQuery();
            List<Integer> recharges = new ArrayList<>(rechargeableItems);
            while (rs.next()) {
                if (ii.isThrowingStar(rs.getInt("itemid")) || ii.isShootingBullet(rs.getInt("itemid"))) {
                    MapleShopItem starItem = new MapleShopItem((short) 1, rs.getInt("itemid"), rs.getInt("price"));
                    ret.addItem(starItem);
                    if (rechargeableItems.contains(starItem.getItemId())) {
                        recharges.remove(Integer.valueOf(starItem.getItemId()));
                    }
                } else {
                    ret.addItem(new MapleShopItem((short) 1000, rs.getInt("itemid"), rs.getInt("price")));
                }
            }
            for (Integer recharge : recharges) {
                ret.addItem(new MapleShopItem((short) 1000, recharge, 0));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            log.error("Could not load shop", e);
        }

        return ret;
    }

    public void addItem(MapleShopItem item) {
        items.add(item);
    }

    public void sendShop(MapleClient c) {
        c.getPlayer().setShop(this);
        c.sendPacket(MaplePacketCreator.getNPCShop(c, npcId, items));
    }

    public void buy(MapleClient c, int itemId, short quantity) {
        if (quantity <= 0) {
            log.warn(c.getPlayer().getName() + " is buying an invalid amount: " + quantity + " of itemid: " + itemId);
            c.disconnect();
            return;
        }
        MapleShopItem item = findById(itemId);
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        if (item != null && item.getPrice() > 0 && c.getPlayer().getMeso() >= item.getPrice() * quantity) {
            if (item.getPrice() * quantity < 0) {
                c.getPlayer().ban(c.getPlayer().getName() + " was auto banned for shop hacking.");
                return;
            }
            if (MapleInventoryManipulator.checkSpace(c, itemId, quantity, "")) {
                if (itemId >= 5000000 && itemId <= 5000100) {
                    if (quantity > 1) {
                        quantity = 1;
                    }
                    MapleInventoryManipulator.addById(c, itemId, quantity, "Pet was purchased.", null, MaplePet.createPet(c.getPlayer().getId(), itemId));
                } else if (ii.isRechargable(itemId)) {
                    short rechquantity = ii.getSlotMax(c, item.getItemId());
                    MapleInventoryManipulator.addById(c, itemId, rechquantity, "Rechargable item purchased.", null, null);
                } else {
                    MapleInventoryManipulator.addById(c, itemId, quantity, c.getPlayer().getName() + " bought " + quantity + " for " + item.getPrice() * quantity + " from shop " + id, null, null);
                }
                c.getPlayer().gainMeso(-(item.getPrice() * quantity), false);
                c.sendPacket(MaplePacketCreator.confirmShopTransaction((byte) 0));
            } else {
                c.sendPacket(MaplePacketCreator.confirmShopTransaction((byte) 3));
            }
        }
    }

    public void sell(MapleClient c, MapleInventoryType type, short slot, short quantity) {
        if (quantity == 0xFFFF || quantity == 0) {
            quantity = 1;
        }
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        IItem item = c.getPlayer().getInventory(type).getItem(slot);
        if (item == null) {
            return;
        }
        if (ii.isThrowingStar(item.getItemId()) || ii.isShootingBullet(item.getItemId())) {
            quantity = item.getQuantity();
        }
        if (quantity < 0) {
            log.warn(c.getPlayer().getName() + " is selling " + quantity + " " + item.getItemId() + " (" + type.name() + "/" + slot + ")");
            c.disconnect();
            return;
        }
        short iQuant = item.getQuantity();
        if (iQuant == 0xFFFF) {
            iQuant = 1;
        }
        if (quantity <= iQuant && iQuant > 0) {
            MapleInventoryManipulator.removeFromSlot(c, type, slot, quantity, false);
            double price;
            if (ii.isThrowingStar(item.getItemId()) || ii.isShootingBullet(item.getItemId())) {
                price = ii.getWholePrice(item.getItemId()) / (double) ii.getSlotMax(c, item.getItemId());
            } else {
                price = ii.getPrice(item.getItemId());
            }
            int recvMesos = (int) Math.max(Math.ceil(price * quantity), 0);
            if (price != -1 && recvMesos > 0) {
                c.getPlayer().gainMeso(recvMesos, false);
            }
            c.sendPacket(MaplePacketCreator.confirmShopTransaction((byte) 0x8));
        }
    }

    public void recharge(MapleClient c, short slot) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        IItem item = c.getPlayer().getInventory(MapleInventoryType.USE).getItem(slot);
        if (item == null || !ii.isThrowingStar(item.getItemId()) && !ii.isShootingBullet(item.getItemId())) {
            if (item != null && (!ii.isThrowingStar(item.getItemId()) || !ii.isShootingBullet(item.getItemId()))) {
                log.warn(c.getPlayer().getName() + " is trying to recharge " + item.getItemId());
            }
            return;
        }
        short slotMax = ii.getSlotMax(c, item.getItemId());

        if (item.getQuantity() < 0) {
            log.warn(c.getPlayer().getName() + " is trying to recharge " + item.getItemId() + " with quantity " + item.getQuantity());
        }
        if (item.getQuantity() < slotMax) {
            // calc price ;_;
            int price = (int) Math.round(ii.getPrice(item.getItemId()) * (slotMax - item.getQuantity()));
            if (c.getPlayer().getMeso() >= price) {
                item.setQuantity(slotMax);
                c.sendPacket(MaplePacketCreator.updateInventorySlot(MapleInventoryType.USE, item));
                c.getPlayer().gainMeso(-price, false, true, false);
                c.sendPacket(MaplePacketCreator.confirmShopTransaction((byte) 0x8));
            }
        }
    }

    protected MapleShopItem findById(int itemId) {
        for (MapleShopItem item : items) {
            if (item.getItemId() == itemId) {
                return item;
            }
        }
        return null;
    }

    public int getNpcId() {
        return npcId;
    }

    public int getId() {
        return id;
    }
}