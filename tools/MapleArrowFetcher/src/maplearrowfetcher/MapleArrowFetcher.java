/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package maplearrowfetcher;

import life.MapleLifeFactory;
import life.MapleMonsterStats;
import tools.DatabaseConnection;
import tools.Pair;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.Map.Entry;

/**
 *
 * @author RonanLana
 * 
 * This application traces arrow drop data on the underlying DB (that must be
 * defined on the DatabaseConnection file of this project) and generates a SQL file
 * that proposes updated arrow quantitty on drop entries for the drop_data table.
 * 
 * The arrow quantity range is calculated accordingly with the target mob stats, such
 * as level and if it's a boss or not.
 * 
 */

public class MapleArrowFetcher {
    
    private static PrintWriter printWriter;
    private static String newFile = "lib/arrow_drop_data.sql";
    
    private static int minArrowId = 2060000;
    private static int maxArrowId = 2061004;
    
    private static float correctionFactor = 2.2f;
    
    private static Map<Integer, MapleMonsterStats> mobStats;
    private static Map<Integer, Pair<Integer, Integer>> mobRange = new HashMap<>();
    
    private static Pair<Integer, Integer> calcArrowRange(int level, boolean boss) {
        int minRange, maxRange;
        
        // MIN range
        minRange = (int)Math.ceil(((2.870503597 * level) - 1.870503597) * (boss ? 1.4 : 1.0) / correctionFactor);
        
        // MAX range
        maxRange = (int)Math.ceil(1.25 * minRange);
        
        return new Pair<>(minRange, maxRange);
    }
    
    private static void calcAllMobsArrowRange() {
        System.out.print("Calculating range... ");
        
        for(Entry<Integer, MapleMonsterStats> mobStat : mobStats.entrySet()) {
            MapleMonsterStats mms = mobStat.getValue();
            Pair<Integer, Integer> arrowRange;
            
            arrowRange = calcArrowRange(mms.getLevel(), mms.isBoss());
            mobRange.put(mobStat.getKey(), arrowRange);
        }
        
        System.out.println("done!");
    }
    
    private static void printSqlHeader() {
        printWriter.println(" # SQL File autogenerated from the MapleArrowFetcher feature by Ronan Lana.");
        printWriter.println(" # Generated data takes into account mob stats such as level and boss for the raw arrow ranges.");
        printWriter.println(" # Only current arrows entries on the DB it was compiled are being updated here.");
        printWriter.println();
        
        printWriter.println("UPDATE drop_data");
        printWriter.println("SET minimum_quantity = CASE");
    }
    
    private static void printSqlMiddle() {
        printWriter.println("  ELSE minimum_quantity END,");
        printWriter.println("    maximum_quantity = CASE");
    }
    
    private static void printSqlFooter() {
        printWriter.println("  ELSE maximum_quantity END");
        printWriter.println(";");
    }
    
    private static void updateSqlMobArrowMinEntry(int[] entry) {
        printWriter.println("                       WHEN dropperid = " + entry[0] + " AND itemid = " + entry[1] + " THEN " + entry[2]);
    }
    
    private static void updateSqlMobArrowMaxEntry(int[] entry) {
        printWriter.println("                       WHEN dropperid = " + entry[0] + " AND itemid = " + entry[1] + " THEN " + entry[3]);
    }
    
    private static List<int[]> getArrowEntryValues(Map<Integer, List<Integer>> existingEntries) {
        List<int[]> entryValues = new ArrayList<>(200);
        
        List<Entry<Integer, List<Integer>>> listEntries = new ArrayList<>(existingEntries.entrySet());
        
        Collections.sort(listEntries, (o1, o2) -> {
                int val1 = o1.getKey();
                int val2 = o2.getKey();
                return (val1 < val2 ? -1 : (val1 == val2 ? 0 : 1));
        });
        
        for(Entry<Integer, List<Integer>> ee : listEntries) {
            int mobid = ee.getKey();
            Pair<Integer, Integer> mr = mobRange.get(mobid);
            
            for(Integer itemid : ee.getValue()) {
                int itemWeight = (itemid % 10) + 1;
                
                int[] values = new int[4];
                values[0] = mobid;
                values[1] = itemid;
                
                values[2] = (int) Math.ceil(mr.getLeft()  / itemWeight);   // weighted min quantity
                values[3] = (int) Math.ceil(mr.getRight() / itemWeight);   // weighted max quantity
                
                entryValues.add(values);
            }
        }
        
        return entryValues;
    }
    
    private static void updateMobsArrowRange() {
        System.out.print("Generating updated ranges... ");
        Connection con = DatabaseConnection.getConnection();
        Map<Integer, List<Integer>> existingEntries = new HashMap<>(200);
        
        try {
            // select all arrow drop entries on the DB, to update their values
            PreparedStatement ps = con.prepareStatement("SELECT dropperid, itemid FROM drop_data WHERE itemid >= " + minArrowId + " AND itemid <= " + maxArrowId + " ORDER BY itemid;");
            ResultSet rs = ps.executeQuery();
            
            if (rs.isBeforeFirst()) {
                while(rs.next()) {
                    int mobid = rs.getInt(1);
                    int itemid = rs.getInt(2);
                    
                    if(mobRange.containsKey(mobid)) {
                        List<Integer> em = existingEntries.get(mobid);
                        
                        if(em == null) {
                            em = new ArrayList<>(2);
                            existingEntries.put(mobid, em);
                        }
                        
                        em.add(itemid);
                    }
                }
                
                if(!existingEntries.isEmpty()) {
                    List<int[]> entryValues = getArrowEntryValues(existingEntries);
                    
                    printWriter = new PrintWriter(newFile, "UTF-8");
                    printSqlHeader();
                    
                    for(int[] arrowEntry : entryValues) {
                        updateSqlMobArrowMinEntry(arrowEntry);
                    }
                    
                    printSqlMiddle();
                    
                    for(int[] arrowEntry : entryValues) {
                        updateSqlMobArrowMaxEntry(arrowEntry);
                    }
                    
                    printSqlFooter();
                    
                    printWriter.close();
                } else {
                    throw new Exception("NO DATA");
                }
                
            } else {
                throw new Exception("NO DATA");
            }
            
            rs.close();
            ps.close();
            con.close();
            
            System.out.println("done!");
            
        } catch(Exception e) {
            if(e.getMessage() != null && e.getMessage().equals("NO DATA")) {
                System.out.println("failed! The DB has no arrow entry to be updated.");
            } else {
                e.printStackTrace();
            }
        }
    }
    
    public static void main(String[] args) {
        // load mob stats from WZ
        mobStats = MapleLifeFactory.getAllMonsterStats();
        
        calcAllMobsArrowRange();
        updateMobsArrowRange();
    }
}
