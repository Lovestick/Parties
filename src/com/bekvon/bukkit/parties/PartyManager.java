/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.bekvon.bukkit.parties;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.entity.Player;

/**
 *
 * @author Administrator
 */
public class PartyManager implements Serializable {

    private Map<String,PlayerParty> parties;
    private Map<String,String> playersParty;
    private Map<String,PartyType> ptype;
    private Map<String,String> tprequest;
    private Map<String,String> inviterequest;
    private Map<String,String> invitedby;
    private Map<String,Boolean> partyChatToggled;
    private boolean tpEnabled;
    private boolean chatEnabled;
    private boolean pvpEnabled;

    public PartyManager() {
        parties = new HashMap<String,PlayerParty>();
        playersParty = new HashMap<String,String>();
        ptype = new HashMap<String,PartyType>();
        tprequest = new HashMap<String,String>();
        inviterequest = new HashMap<String,String>();
        invitedby = new HashMap<String,String>();
        partyChatToggled = new HashMap<String,Boolean>();
        pvpEnabled = false;
        chatEnabled = true;
    }

    public synchronized void createParty(String partyName, String creator, PartyType partyType)
    {
        partyName = partyName.toLowerCase();
        creator = creator.toLowerCase();
        Player player = Parties.getPlayer(creator);
        if(player == null)
            return;
        boolean hasDeny = Parties.hasAuthority(player, "parties.deny.create", false);
        boolean hasAllow = Parties.hasAuthority(player, "parties.allow.create", false);
        if(!hasAllow && (hasDeny))
        {
            player.sendMessage("§cYou dont have access to party creation.");
            return;
        }
        if(isPlayerInParty(creator))
        {
            player.sendMessage("§cYou are already in a party!");
            return;
        }
        if(partyExists(partyName))
        {
            player.sendMessage("§cError, this party name already exists...");
            return;
        }
        parties.put(partyName, new PlayerParty(partyName,creator));
        ptype.put(partyName, partyType);
        playersParty.put(creator, partyName);
        player.sendMessage("§aParty " + partyName + " has been created.");
    }
    
    public synchronized void removeParty(String partyName)
    {
        partyName = partyName.toLowerCase();
        if(partyExists(partyName))
        {
            PlayerParty party = getParty(partyName);
            String[] players = party.getPlayerList();
            for(int i = 0; i < players.length; i ++)
            {
                if(tprequest.containsKey(players[i]))
                    tprequest.remove(players[i]);
                if(invitedby.containsKey(players[i]))
                    invitedby.remove(players[i]);
                if(playersParty.containsKey(players[i]))
                    playersParty.remove(players[i]);
                Iterator<Entry<String, String>> thisit = inviterequest.entrySet().iterator();
                while(thisit.hasNext())
                {
                    Entry<String, String> next = thisit.next();
                    if(next.getValue().equals(players[i]))
                    {
                        thisit.remove();
                    }
                }
            }
            party.removeAllFromParty();
            parties.remove(partyName);
            ptype.remove(partyName);
        }
    }

    public synchronized boolean partyExists(String partyName)
    {
        partyName = partyName.toLowerCase();
        return parties.containsKey(partyName);
    }

    public enum PartyType {
        OPEN,INVITE,LEADER
    }

    public synchronized void joinParty(String addPlayer, String partyName) {
        partyName = partyName.toLowerCase();
        addPlayer = addPlayer.toLowerCase();
        PlayerParty party = getParty(partyName);
        Player player = Parties.getPlayer(addPlayer);
        if (player == null) {
            return;
        }
        boolean hasDeny = Parties.hasAuthority(player, "parties.deny.join", false);
        boolean hasAllow = Parties.hasAuthority(player, "parties.allow.join", false);
        if(!hasAllow && (hasDeny))
        {
            player.sendMessage("§cYou dont have access to join partys.");
            return;
        }
        if (party != null) {
            if (this.isPlayerInParty(addPlayer)) {
                player.sendMessage("§cYou are already in a party!");
                return;
            }
            PartyType type = ptype.get(partyName);
            if (type == PartyType.OPEN) {
                party.addToParty(addPlayer);
                playersParty.put(addPlayer, party.getPartyName());
            } else {
                player.sendMessage("§cThis is not a free-join party...");
            }
        } else {
            player.sendMessage("§cParty does not exist.");
        }
    }

    public synchronized void removePlayerFromParty(String removePlayer, String requestPlayer) {
        removePlayer = removePlayer.toLowerCase();
        requestPlayer = requestPlayer.toLowerCase();
        PlayerParty party = getPlayersParty(requestPlayer);
        Player reqPlayer = Parties.getPlayer(requestPlayer);
        if (party != null) {
            if (removePlayer == null || !party.isInParty(removePlayer)) {
                reqPlayer.sendMessage("§cInvalid target player.");
                return;
            }
            if (removePlayer.equals(requestPlayer) || party.isPartyLeader(requestPlayer)) {
                party.removeFromParty(removePlayer);
                playersParty.remove(removePlayer);
                tprequest.remove(removePlayer);
                if(invitedby.containsKey(removePlayer))
                    invitedby.remove(removePlayer);
            } else {
                reqPlayer.sendMessage("§cYou dont have permission to remove people from the party.");
            }
            if(party.getPartyCount() == 0)
                removeParty(party.getPartyName());
        }
    }

    public synchronized void chatInParty(String sendPlayer, String message)
    {
        sendPlayer = sendPlayer.toLowerCase();
        Player player = Parties.getPlayer(sendPlayer);
        if(player == null || !player.isOnline())
            return;
        boolean hasDeny = Parties.hasAuthority(player, "parties.deny.chat", false);
        boolean hasAllow = Parties.hasAuthority(player, "parties.allow.chat", false);

        if(!hasAllow && (hasDeny || !chatEnabled))
        {
            player.sendMessage("§cYou dont have access to party chat.");
            return;
        }
        if(playersParty.containsKey(sendPlayer))
        {
            PlayerParty party = getPlayersParty(sendPlayer);
            if(party!=null)
            {
                party.sendPartyChat(message, sendPlayer);
            }
        }
        else
        {
            player.sendMessage("§cYou are not in a party.");
        }
    }

    public synchronized void tpRequest(String requestPlayer, String targetPlayer)
    {
        targetPlayer = targetPlayer.toLowerCase();
        requestPlayer = requestPlayer.toLowerCase();
        Player reqPlayer = Parties.getPlayer(requestPlayer);
        if(reqPlayer == null || !reqPlayer.isOnline())
            return;
        boolean hasDeny = Parties.hasAuthority(reqPlayer, "parties.deny.tp", false);
        boolean hasAllow = Parties.hasAuthority(reqPlayer, "parties.allow.tp", false);
        if(!hasAllow && (hasDeny || !chatEnabled))
        {
            reqPlayer.sendMessage("§cYou dont have access to party teleport.");
            return;
        }
        if(requestPlayer.equals(targetPlayer))
        {
            reqPlayer.sendMessage("§cYou cannot teleport to yourself!");
            return;
        }
        Player targPlayer = Parties.getPlayer(targetPlayer);
        PlayerParty party = getPlayersParty(requestPlayer);
        if(party == null)
        {
            reqPlayer.sendMessage("§cYou are not in a party.");
            return;
        }
        if(targetPlayer == null || !party.isInParty(targetPlayer))
        {
            reqPlayer.sendMessage("§cTarget player is not in your party.");
            return;
        }
        if(tprequest.containsKey(targetPlayer))
        {
            String get = tprequest.get(targetPlayer);
            if(targPlayer != null && targPlayer.isOnline())
            {
                if(get.equals(requestPlayer))
                {
                    reqPlayer.sendMessage("§cYou are already requesting a teleport from this player!");
                }
                else
                {
                    reqPlayer.sendMessage("§cSomone else is already requesting a teleport to this player.");
                }
                return;
            }
            else
            {
                tprequest.remove(targetPlayer);
            }
        }
        targPlayer.sendMessage("§a" + requestPlayer + " requests to teleport to you, use /party tpaccept or /party tpdecline.");
        reqPlayer.sendMessage("§aSent teleport request to " + targetPlayer);
        tprequest.put(targetPlayer, requestPlayer);
    }

    public synchronized void sendPartyInvite(String requestPlayer, String targetPlayer) {
        targetPlayer = targetPlayer.toLowerCase();
        requestPlayer = requestPlayer.toLowerCase();
        Player reqPlayer = Parties.getPlayer(requestPlayer);
        if(reqPlayer == null)
            return;
        Player targPlayer = Parties.getPlayer(targetPlayer);
        PlayerParty party = getPlayersParty(requestPlayer);
        if (party == null) {
            reqPlayer.sendMessage("§cYou are not in a party.");
            return;
        }
        if(targPlayer==null)
        {
            reqPlayer.sendMessage("Invalid target player.");
            return;
        }
        boolean hasDeny = Parties.hasAuthority(targPlayer, "parties.deny.join", false);
        boolean hasAllow = Parties.hasAuthority(targPlayer, "parties.allow.join", false);
        if(!hasAllow && (hasDeny))
        {
            reqPlayer.sendMessage("§cTarget person doesn't have access to join parties.");
            return;
        }
        if(ptype.get(party.getPartyName()) == PartyType.LEADER)
        {
            if(!party.isPartyLeader(requestPlayer))
            {
                reqPlayer.sendMessage("§cOnly the party leader can send invites in this party.");
                return;
            }
        }
        if(targPlayer == null || !targPlayer.isOnline())
        {
            reqPlayer.sendMessage("§cTarget player is invalid or offline.");
            return;
        }
        if(isPlayerInParty(targetPlayer))
        {
            reqPlayer.sendMessage("§cTarget player is already in a party.");
            return;
        }
        if (inviterequest.containsKey(targetPlayer)) {
            Player get = Parties.getPlayer(inviterequest.get(targetPlayer));
            if (get != null && get.isOnline()) {
                if (get.equals(reqPlayer)) {
                    reqPlayer.sendMessage("§cYou already invited this player!");
                } else {
                    reqPlayer.sendMessage("§cSomone else is inviting this player.");
                }
                return;
            } else {
                inviterequest.remove(targetPlayer);
            }
        }
        targPlayer.sendMessage("§a" + requestPlayer + " has invited you to party: " + party.getPartyName());
        reqPlayer.sendMessage("§aSent party invite to " + targetPlayer);
        inviterequest.put(targetPlayer, requestPlayer);
    }

    public synchronized void inviteAccept(String targetPlayer) {
        targetPlayer = targetPlayer.toLowerCase();
        Player targPlayer = Parties.getPlayer(targetPlayer);
        if(targPlayer == null)
            return;
        if (inviterequest.containsKey(targetPlayer)) {
            String requestPlayer = inviterequest.get(targetPlayer);
            PlayerParty party = getPlayersParty(requestPlayer);
            Player reqPlayer = Parties.getPlayer(requestPlayer);
            if (reqPlayer == null || !reqPlayer.isOnline()) {
                inviterequest.remove(targetPlayer);
                return;
            }
            party.addToParty(targetPlayer);
            playersParty.put(targetPlayer, party.getPartyName());
            if(reqPlayer != null)
                reqPlayer.sendMessage("§a" + targetPlayer + " accepted your party invite.");
            inviterequest.remove(targetPlayer);
            invitedby.put(targetPlayer, requestPlayer);
            targPlayer.sendMessage("§aYou accepted " + requestPlayer + "'s party invite.");
        } else {
            targPlayer.sendMessage("§cYou have no pending party invite.");
        }
    }

    public synchronized void inviteDecline(String targetPlayer) {
        targetPlayer = targetPlayer.toLowerCase();
        Player targPlayer = Parties.getPlayer(targetPlayer);
        if(targPlayer == null)
            return;
        if (inviterequest.containsKey(targetPlayer)) {
            String requestPlayer = inviterequest.get(targetPlayer);
            Player reqPlayer = Parties.getPlayer(requestPlayer);
            if (reqPlayer != null && reqPlayer.isOnline()) {
                reqPlayer.sendMessage("§c" + targetPlayer + " declined your party invite.");
            }
            inviterequest.remove(targetPlayer);
            targPlayer.sendMessage("§cYou declined " + requestPlayer + "'s party invite.");
        } else {
            targPlayer.sendMessage("§cYou have no pending invite request.");
        }
    }


    public synchronized void tpAccept(String targetPlayer)
    {
        targetPlayer = targetPlayer.toLowerCase();
        Player targPlayer = Parties.getPlayer(targetPlayer);
        if(targPlayer == null)
            return;
        if(tprequest.containsKey(targetPlayer))
        {
            String requestPlayer = tprequest.get(targetPlayer);
            Player reqPlayer = Parties.getPlayer(requestPlayer);
            PlayerParty party = getPlayersParty(requestPlayer);
            if(party == null)
            {
                tprequest.remove(targetPlayer);
                return;
            }
            party.partyTP(requestPlayer, targetPlayer);
            if(reqPlayer!=null)
                reqPlayer.sendMessage("§a" + targetPlayer + " accepted your teleport request.");
            tprequest.remove(targetPlayer);
            targPlayer.sendMessage("§aYou accepted " + requestPlayer + "'s teleport request.");
        }
        else
        {
            targPlayer.sendMessage("§cYou have no pending teleport request.");
        }
    }

    public synchronized void tpDecline(String targetPlayer) {
        targetPlayer = targetPlayer.toLowerCase();
        Player targPlayer = Parties.getPlayer(targetPlayer);
        if(targPlayer == null)
            return;
        if (tprequest.containsKey(targetPlayer)) {
            String requestPlayer = tprequest.get(targetPlayer);
            Player reqPlayer = Parties.getPlayer(requestPlayer);
            if(reqPlayer != null && reqPlayer.isOnline())
                reqPlayer.sendMessage("§c" + targetPlayer + " declined your teleport request.");
            tprequest.remove(targetPlayer);
            targPlayer.sendMessage("§cYou declined " + reqPlayer + "'s teleport request.");
        } else {
            targPlayer.sendMessage("§cYou have no pending teleport request.");
        }
    }


    public synchronized boolean isPlayerInParty(String playerName)
    {
        playerName = playerName.toLowerCase();
        return playersParty.containsKey(playerName);
    }

    public synchronized String[] getPartyList()
    {
        String list[] = new String[parties.size()];
        Iterator<String> thisit = parties.keySet().iterator();
        int i = 0;
        while(thisit.hasNext())
        {
            list[i] = thisit.next();
            i++;
        }
        return list;
    }

    public synchronized String[] getPlayersPartyList(String playerName)
    {
        if(isPlayerInParty(playerName))
        {
            return getPlayersParty(playerName).getPlayerList();
        }
        return null;
    }

    public synchronized void disbandParty(String player)
    {
        player = player.toLowerCase();
        Player p = Parties.getPlayer(player);
        if(p==null)
            return;
        if(!isPlayerInParty(player))
        {
            p.sendMessage("§cYou are not in a party.");
            return;
        }
        PlayerParty party = getPlayersParty(player);
        if(party.isPartyLeader(player))
        {
            removeParty(party.getPartyName());
        }
        else
        {
            p.sendMessage("§cYou are not party leader.");
        }
    }

    public synchronized void changePartyLeader(String reqPlayer, String targetPlayer)
    {
        reqPlayer = reqPlayer.toLowerCase();
        targetPlayer = targetPlayer.toLowerCase();
        Player requestPlayer = Parties.getPlayer(reqPlayer);
        if(!isPlayerInParty(reqPlayer))
        {
            requestPlayer.sendMessage("§cYou are not in a party.");
            return;
        }
        PlayerParty party = getPlayersParty(reqPlayer);
        if(party.isPartyLeader(reqPlayer))
        {
            if(!party.isInParty(targetPlayer))
            {
                requestPlayer.sendMessage("§cTarget player is not in the party.");
                return;
            }
            party.setPartyLeader(targetPlayer);
        }
        else
        {
            requestPlayer.sendMessage("§cYou are not the party leader.");
        }
    }

    public synchronized void changePartyType(String reqPlayer, PartyType intype) {
        reqPlayer = reqPlayer.toLowerCase();
        Player requestPlayer = Parties.getPlayer(reqPlayer);
        if(requestPlayer == null)
            return;
        if (!isPlayerInParty(reqPlayer)) {
            requestPlayer.sendMessage("§cYou are not in a party.");
            return;
        }
        PlayerParty party = getPlayersParty(reqPlayer);
        if (party.isPartyLeader(reqPlayer)) {
            ptype.put(party.getPartyName(), intype);
        } else {
            requestPlayer.sendMessage("§cYou are not the party leader.");
        }
    }

    public synchronized void renameParty(String reqPlayer, String newName) {
        reqPlayer = reqPlayer.toLowerCase();
        newName = newName.toLowerCase();
        Player requestPlayer = Parties.getPlayer(reqPlayer);
        if(requestPlayer == null)
            return;
        if (!isPlayerInParty(reqPlayer)) {
            requestPlayer.sendMessage("§cYou are not in a party.");
            return;
        }

        PlayerParty party = getPlayersParty(reqPlayer);
        if (party.isPartyLeader(reqPlayer)) {
            if (partyExists(newName)) {
                requestPlayer.sendMessage("§cAnother party by that name already exists.");
                return;
            }
            ptype.put(newName, ptype.remove(party.getPartyName()));
            parties.remove(party.getPartyName());
            party.setPartyName(newName);
            parties.put(newName, party);
            String[] playerList = party.getPlayerList();
            for( int i = 0; i < playerList.length; i++)
            {
                playersParty.put(playerList[i], newName);
            }
        } else {
            requestPlayer.sendMessage("§cYou are not the party leader.");
        }
    }

    public synchronized void disbandAllPartys()
    {
        for(int i = 0; i < parties.size(); i ++)
        {
            Iterator<PlayerParty> pit = parties.values().iterator();
            while(pit.hasNext())
            {
                PlayerParty next = pit.next();
                next.removeAllFromParty();
                pit.remove();
            }
        }
        parties.clear();
        ptype.clear();
        tprequest.clear();
        inviterequest.clear();
        invitedby.clear();
    }

    private synchronized PlayerParty getPlayersParty(String playerName)
    {
        playerName = playerName.toLowerCase();
        if(playersParty.containsKey(playerName))
        {
            return parties.get(playersParty.get(playerName));
        }
        return null;
    }

    private synchronized PlayerParty getParty(String partyName)
    {
        partyName = partyName.toLowerCase();
        if(partyExists(partyName))
            return parties.get(partyName);
        return null;
    }

    public synchronized static void serialize(PartyManager pman, File saveFile) {
        try
        {
            if (saveFile.isFile()) {
                saveFile.delete();
            }
            FileOutputStream fout = new FileOutputStream(saveFile);
            ObjectOutputStream writer = new java.io.ObjectOutputStream(fout);
            writer.writeInt(pman.parties.size());
            Iterator<Entry<String, PlayerParty>> pit = pman.parties.entrySet().iterator();
            while (pit.hasNext()) {
                Entry<String, PlayerParty> next = pit.next();
                PlayerParty party = next.getValue();
                writer.writeObject(next.getKey());
                writer.writeObject(party.getPartyLeader());
                writer.writeObject(pman.ptype.get(next.getKey()));
                writer.writeObject(party.getPlayerList());
            }
            writer.writeInt(pman.invitedby.size());
            Iterator<Entry<String, String>> iit = pman.invitedby.entrySet().iterator();
            while(iit.hasNext())
            {
                Entry<String, String> next = iit.next();
                String p1 = next.getKey();
                String p2 = next.getValue();
                writer.writeObject(p1);
                writer.writeObject(p2);
            }
            writer.close();
            fout.close();
            System.out.print("[Parties]: Saved parties to file.");
        }
        catch (Exception ex)
        {
            System.out.print("[Parties]: FAILED to save to file.");
        }
    }

    public synchronized static PartyManager deserialize(File saveFile) {
        try{
            if (!saveFile.isFile()) {
                System.out.println("Parties save file does not exist.");
                return null;
            }
            FileInputStream fin = new FileInputStream(saveFile);
            ObjectInputStream reader = new ObjectInputStream(fin);
            PartyManager pman = new PartyManager();
            int partycount = reader.readInt();
            for(int i = 0; i < partycount; i++)
            {
                String partyName = (String) reader.readObject();
                String partyLeader = (String) reader.readObject();
                PartyType ptype = (PartyType) reader.readObject();
                PlayerParty party = new PlayerParty(partyName,partyLeader);
                String[] plist = (String[]) reader.readObject();
                for(int j = 0; j < plist.length; j ++)
                {
                    party.addToParty(plist[j]);
                    pman.playersParty.put(plist[j], partyName);
                }
                pman.parties.put(partyName, party);
                pman.ptype.put(partyName, ptype);
            }
            int invitesize = reader.readInt();
            for(int i = 0; i < invitesize; i++)
            {
                String p1 = (String) reader.readObject();
                String p2 = (String) reader.readObject();
                pman.invitedby.put(p1, p2);
            }
            System.out.println("[Parties]: save file loaded.");
            reader.close();
            fin.close();
            return pman;
        }
        catch (Exception ex)
        {
            System.out.println("[Parties]: save file INVALID!");
            return null;
        }
    }

    public synchronized void sendPartyMessage(String partyName, String message)
    {
        partyName = partyName.toLowerCase();
        PlayerParty party = this.getParty(partyName);
        if(party != null)
            party.sendPartyMessage(message);
    }

    public synchronized String getPlayersPartyName(String player)
    {
        player = player.toLowerCase();
        if(playersParty.containsKey(player))
            return playersParty.get(player);
        return null;
    }

    public synchronized boolean partyChatEnabled(String player)
    {
        player = player.toLowerCase();
        if(partyChatToggled.containsKey(player))
        {
            boolean b = partyChatToggled.get(player);
            return b;
        }
        return false;
    }

    public synchronized void setPartyChatEnabled(String player, boolean enable)
    {
        player = player.toLowerCase();
        partyChatToggled.put(player, enable);
    }

    public synchronized void setPvpEnabled(boolean enable)
    {
        pvpEnabled = enable;
    }

    public synchronized void setChatEnabled(boolean enable)
    {
        chatEnabled = enable;
    }

    public synchronized void setTpEnabled(boolean enable)
    {
        tpEnabled = enable;
        if(!tpEnabled)
        {
            tprequest.clear();
        }
    }

    public synchronized boolean isPvpEnabled()
    {
        return pvpEnabled;
    }

    public synchronized boolean isTpEnabled()
    {
        return tpEnabled;
    }

    public synchronized boolean isChatEnabled()
    {
        return chatEnabled;
    }

}
