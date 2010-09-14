///////////////////////////////////////////////////////////////////////////////
//  Copyright (C) 2010 Taesun Moon, The University of Texas at Austin
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
///////////////////////////////////////////////////////////////////////////////
package opennlp.textgrounder.gazetteers.old;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

import java.sql.*;
import org.sqlite.JDBC;
import org.sqlite.*;

import gnu.trove.*;
import opennlp.textgrounder.geo.CommandLineOptions;

import opennlp.textgrounder.topostructs.*;
import opennlp.textgrounder.util.*;

/**
 * Read in a TR-CoNLL gazetteer, described in Toponym Resolution in Text by
 * Jochen Leidner.
 */
public class TRGazetteer extends Gazetteer {

    private static Coordinate nullCoord = new Coordinate(0.0, 0.0);

    private static final double MAX_DIFF = 0.1; // for comparing two coordinates; if lat and lon both differ by <= MAX_DIFF, they are considered equal

    /*public TRGazetteer(CommandLineOptions options) throws FileNotFoundException,
          IOException, ClassNotFoundException, SQLException {
        super(options);
        initialize(options.getGazetteerPath());
	}*/

    public TRGazetteer(String location) {
	super(location);
	try {
	    initialize(location);
	}
	catch(Exception e) {
	    e.printStackTrace();
	    System.exit(1);
	}
    }

    public TRGazetteer(String location, boolean gazetteerRefresh) {
	super(location);
        this.gazetteerRefresh = gazetteerRefresh;
	try {
	    initialize(location);
	}
	catch(Exception e) {
	    e.printStackTrace();
	    System.exit(1);
	}
    }

    @Override
    protected void initialize(String location) throws FileNotFoundException,
          IOException, ClassNotFoundException, SQLException {

        System.out.println("Populating TR-Gazetteer from " + location + " ...");

        Class.forName("org.sqlite.JDBC");

        conn = DriverManager.getConnection("jdbc:sqlite:" + location);

        stat = conn.createStatement();

	//ResultSet rs = stat.executeQuery("select * from places");
	//ResultSet rs = stat.executeQuery("select count(*) as rowcount from places");
	ResultSet rs = stat.executeQuery("SELECT name FROM sqlite_master WHERE name='places'");
	if(rs.next() && !gazetteerRefresh) {
	    int rowCount = 0;
	    //int rowCount = rs.getInt("rowcount");
	    //if(rowCount > 0) {
	    System.out.println("Using pre-populated TRGazetteer database.");
	    System.out.print("  Adding location IDs to Gazetteer hashset for quick checking...");
	    rs.close();
	    rs = stat.executeQuery("select * from places");
	    while(rs.next()) {
		rowCount++;
		String name = rs.getString("name");
		if(name != null) {
		    int topidx = toponymLexicon.addWord(name);
		    put(topidx, null);
		}
	    }
	    System.out.println("done. " + rowCount + " total entries.");
	    return;
	}
	rs.close();


        if(gazetteerRefresh)
            System.out.println("REFRESHING gazetteer from original database.");

        TIntObjectHashMap<Coordinate> uniqueLocations = new TIntObjectHashMap<Coordinate>();

        stat.executeUpdate("drop table if exists places;");
        stat.executeUpdate("create table places (id integer primary key, name, type, lat float, lon float, pop int, container);");
        PreparedStatement prep = conn.prepareStatement("insert into places values (?, ?, ?, ?, ?, ?, ?);");

        int placeId = 1;
        int ignoreCount = 0;


        // get places from CIA centroids section:
        System.out.println(" Reading CIA centroids section...");
        rs = stat.executeQuery("select * from T_CIA_CENTROIDS");
        while (rs.next()) {
            prep.setInt(1, placeId);
            String nameToInsert = rs.getString("COUNTRY").toLowerCase();
            if (nameToInsert != null) {
                prep.setString(2, nameToInsert);
            }
            prep.setString(3, "cia_centroid");
            double latToInsert = DMDtoDD(rs.getInt("LAT_DEG"), rs.getInt("LAT_MIN"), rs.getString("LAT_DIR"));/////
            double longToInsert = DMDtoDD(rs.getInt("LONG_DEG"), rs.getInt("LONG_MIN"), rs.getString("LONG_DIR"));///////

            if (latToInsert < -180.0 || latToInsert > 180.0
                  || longToInsert < -90.0 || longToInsert > 90.0) {
                ignoreCount++;
                continue;
            }

            int topidx = toponymLexicon.addWord(nameToInsert);
            Coordinate prevCoord = uniqueLocations.get(topidx);
            Coordinate newCoord = new Coordinate(longToInsert, latToInsert);
            if(prevCoord != null) {
                if(prevCoord.looselyMatches(newCoord, MAX_DIFF)) {
                    ignoreCount++;
                    continue; // skip entries with the same name and coordinate
                }
            }
            else
                uniqueLocations.put(topidx, newCoord);

            prep.setDouble(4, latToInsert);
            prep.setDouble(5, longToInsert);
            prep.addBatch();

            if (placeId % 100 == 0) {
                System.out.println("  Added " + placeId + " places...");// gazetteer has " + populations.size() + " entries so far.");
                System.out.println("    Last added: " + nameToInsert + ", cia_centroid, (" + latToInsert + ", " + longToInsert + ")");
            }

            placeId++;

            put(topidx, null);
        }


        // get places from USGS section:
        System.out.println(" Reading USGS section...");
        rs = stat.executeQuery("select * from T_USGS_PP");
        while (rs.next()) {
            String rawType = rs.getString("FeatureType");
            String typeToInsert = "";
            if (rawType != null) {
                if (rawType.equals("ppl")) {
                    typeToInsert = "locality";
                } else {
                    continue; // skipping non-localities for now
                    //typeToInsert = "NON-locality";
                }
                prep.setString(3, typeToInsert);
            }
            prep.setInt(1, placeId);
            String nameToInsert = rs.getString("FeatureName").toLowerCase();
            if (nameToInsert != null) {
                prep.setString(2, nameToInsert);
            }
            double latToInsert = rs.getDouble("PrimaryLatitudeDD");///////
            double longToInsert = rs.getDouble("PrimaryLongitudeDD");//////

            if (latToInsert < -180.0 || latToInsert > 180.0
                  || longToInsert < -90.0 || longToInsert > 90.0) {
                ignoreCount++;
                continue;
            }

            int topidx = toponymLexicon.addWord(nameToInsert);
            Coordinate prevCoord = uniqueLocations.get(topidx);
            Coordinate newCoord = new Coordinate(longToInsert, latToInsert);
            if(prevCoord != null) {
                if(prevCoord.looselyMatches(newCoord, MAX_DIFF)) {
                    ignoreCount++;
                    continue; // skip entries with the same name and coordinate
                }
            }
            else
                uniqueLocations.put(topidx, newCoord);

            prep.setDouble(4, latToInsert);
            prep.setDouble(5, longToInsert);

            int popToInsert = rs.getInt("EstimatedPopulation");
            if (popToInsert != 0) {
                prep.setDouble(6, popToInsert);
                //System.out.println(popToInsert);
            }

            prep.addBatch();

            /*if(nameToInsert.equals("texas") || nameToInsert.equals("california"))
            System.out.println(nameToInsert + ", " + typeToInsert + ", (" + latToInsert + ", " + longToInsert + "), " + popToInsert);*/

            if (placeId % 50000 == 0) {
                System.out.println("  Added " + placeId + " places...");// gazetteer has " + populations.size() + " entries so far.");
                System.out.println("    Last added: " + nameToInsert + ", " + typeToInsert + ", (" + latToInsert + ", " + longToInsert + "), " + popToInsert);
            }

            placeId++;

            put(topidx, null);
        }


        // get places from NGA section:
        System.out.println(" Reading NGA section...");
        rs = stat.executeQuery("select * from T_NGA");
        while (rs.next()) {
            String rawType = rs.getString("FC");
            String typeToInsert = "";
            if (rawType != null) {
                if (rawType.equals("P")) {
                    typeToInsert = "locality";
                } else {
                    continue; // skipping non-localities for now
                    //typeToInsert = "NON-locality";
                }
                prep.setString(3, typeToInsert);
            }
            prep.setInt(1, placeId);
            String nameToInsert = rs.getString("FULL_NAME_ND").toLowerCase();
            if (nameToInsert != null) {
                prep.setString(2, nameToInsert);
            }

            double latToInsert = rs.getDouble("DD_LAT");//"DD_LAT"); //switched?
            double longToInsert = rs.getDouble("DD_LONG");//"DD_LONG");

            if (latToInsert < -180.0 || latToInsert > 180.0
                  || longToInsert < -90.0 || longToInsert > 90.0) {
                ignoreCount++;
                continue;
            }
            
            int topidx = toponymLexicon.addWord(nameToInsert);
            Coordinate prevCoord = uniqueLocations.get(topidx);
            Coordinate newCoord = new Coordinate(longToInsert, latToInsert);
            if(prevCoord != null) {
                if(prevCoord.looselyMatches(newCoord, MAX_DIFF)) {
                    ignoreCount++;
                    continue; // skip entries with the same name and coordinate
                }
            }
            else
                uniqueLocations.put(topidx, newCoord);

            prep.setDouble(4, latToInsert);
            prep.setDouble(5, longToInsert);

            int popToInsert = rs.getInt("DIM"); // note: DIM holds population for type P and elevation for all others
            if (popToInsert != 0 && rawType != null && rawType.equals("P")) {
                prep.setInt(6, popToInsert);
                //System.out.println(popToInsert);
            }
            // could use ADM2 for container, but those are counties, not countries, so probably a bad idea. leaving container field blank for now
            prep.addBatch();


            /*if(nameToInsert.equals("texas") || nameToInsert.equals("california"))
            System.out.println(nameToInsert + ", " + typeToInsert + ", (" + latToInsert + ", " + longToInsert + "), " + popToInsert);*/

            if (placeId % 100000 == 0) {
                System.out.println("  Added " + placeId + " places...");// gazetteer has " + populations.size() + " entries so far.");
                System.out.println("    Last added: " + nameToInsert + ", " + typeToInsert + ", (" + latToInsert + ", " + longToInsert + "), " + popToInsert);
            }
            //if(placeId == 1000)//////////////////
            //  System.exit(0);//////////////////

            placeId++;

            put(topidx, null);
        }



        if (ignoreCount > 0) {
            System.out.println(ignoreCount + " (" + ((ignoreCount * 100) / (ignoreCount + placeId)) + "%) entries were ignored due to invalid coordinates (out of range) or duplicate entries");
        }

        System.out.print("Executing batch insertion into database...");
        conn.setAutoCommit(false);
        prep.executeBatch();
        conn.setAutoCommit(true);
        System.out.println("done.");

        System.out.println("Number of entries in database = " + (placeId - 1));

    }
    /**
     * Converts (half) a coordinate from degrees-minutes-direction to decimal degrees
     */
    private double DMDtoDD(int degrees, int minutes, String direction) {
        double dd = degrees;
        dd += ((double) minutes) / 60.0;
        if (direction.equals("S") || direction.equals("W")) {
            dd *= -1;
        }
        return dd;
    }

    protected void finalize() throws Throwable {
        try {
            conn.close();
        } finally {
            super.finalize();
        }
    }
}