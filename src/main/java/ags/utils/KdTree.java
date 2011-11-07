/**
 * Copyright 2009 Rednaxela
 * 
 * This software is provided 'as-is', without any express or implied
 * warranty. In no event will the authors be held liable for any damages
 * arising from the use of this software.
 * 
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 * 
 *    1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 
 *    2. This notice may not be removed or altered from any source
 *    distribution.
 */
 
package ags.utils;

import java.util.Arrays;
import java.util.Collections; 
import java.util.ArrayList;
import java.util.List;
 
/**
 * An efficient well-optimized kd-tree
 * 
 * @author Rednaxela
 */
public class KdTree<T> {
    // Static variables
    private static final int           bucketSize = 935;
 
    // All types
    private final int                  dimensions;
    private final KdTree<T>            parent;
 
    // Leaf only
    private double[][]                 locations;
    private Object[]                   data;
    private int                        locationCount;
 
    // Stem only
    private KdTree<T>                  left, right;
    private int                        splitDimension;
    private double                     splitValue;
 
    // Bounds
    public double[]                    minLimit, maxLimit;
    private boolean                    singularity;

    /**
     * Construct a KdTree with a given number of dimensions and a limit on
     * maxiumum size (after which it throws away old points)
     */
    public KdTree(int dimensions) {
        this.dimensions = dimensions;
 
        // Init as leaf
        this.locations = new double[bucketSize][];
        this.data = new Object[bucketSize];
        this.locationCount = 0;
        this.singularity = true;
 
        // Init as root
        this.parent = null;
    }
 
    /**
     * Constructor for child nodes. Internal use only.
     */
    private KdTree(KdTree<T> parent, boolean right) {
        this.dimensions = parent.dimensions;
 
        // Init as leaf
        this.locations = new double[Math.max(bucketSize, parent.locationCount)][];
        this.data = new Object[Math.max(bucketSize, parent.locationCount)];
        this.locationCount = 0;
        this.singularity = true;
 
        // Init as non-root
        this.parent = parent;
    }
 
    /**
     * Get the number of points in the tree
     */
    public int size() {
        return locationCount;
    }

    public ArrayList<T> getData() {
        ArrayList<T> outData = new ArrayList<T>();
        if (data == null)
        	return outData;
        for (int i = 0; i < locationCount; i++) {
            // No way around the warning other than either to suppress it,
            // or not use Object[] arrays.
            @SuppressWarnings("unchecked")
            T suppress_warning = (T) data[i];
        	outData.add(suppress_warning);
        }
        return outData;
    }

    public KdTree<T> getLeaf(double[] location) {
        if (left == null || right == null)
            return this;
        else if (location[splitDimension] <= splitValue)
            return left.getLeaf(location);
        else
            return right.getLeaf(location);
    }

    /**
     * Add a point and associated value to the tree
     */
    public void addPoint(double[] location, T value) {
        KdTree<T> cursor = this;
 
        while (cursor.locations == null || cursor.locationCount >= cursor.locations.length) {
            if (cursor.locations != null) {
                cursor.splitDimension = cursor.findWidestAxis();
                //cursor.splitValue = (cursor.minLimit[cursor.splitDimension] + cursor.maxLimit[cursor.splitDimension]) * 0.5;

								List<Double> list = new ArrayList<Double>();
                for(int i=0;i<cursor.locations.length;i++)
                	list.add(cursor.locations[i][cursor.splitDimension]);
                Collections.sort(list);
								if(list.size()%2 == 1)
                	cursor.splitValue = list.get(list.size()/2);
								else{
                	cursor.splitValue = (list.get(list.size()/2) + list.get(list.size()/2 - 1))/2;
								}

                // Never split on infinity or NaN
                if (cursor.splitValue == Double.POSITIVE_INFINITY) {
                    cursor.splitValue = Double.MAX_VALUE;
                }
                else if (cursor.splitValue == Double.NEGATIVE_INFINITY) {
                    cursor.splitValue = -Double.MAX_VALUE;
                }
                else if (Double.isNaN(cursor.splitValue)) {
                    cursor.splitValue = 0;
                }
 
                // Don't split node if it has no width in any axis. Double the
                // bucket size instead
                if (cursor.minLimit[cursor.splitDimension] == cursor.maxLimit[cursor.splitDimension]) {
                    double[][] newLocations = new double[cursor.locations.length * 2][];
                    System.arraycopy(cursor.locations, 0, newLocations, 0, cursor.locationCount);
                    cursor.locations = newLocations;
                    Object[] newData = new Object[newLocations.length];
                    System.arraycopy(cursor.data, 0, newData, 0, cursor.locationCount);
                    cursor.data = newData;
                    break;
                }
 
                // Don't let the split value be the same as the upper value as
                // can happen due to rounding errors!
                if (cursor.splitValue == cursor.maxLimit[cursor.splitDimension]) {
                    cursor.splitValue = cursor.minLimit[cursor.splitDimension];
                }
 
                // Create child leaves
                KdTree<T> left = new ChildNode(cursor, false);
                KdTree<T> right = new ChildNode(cursor, true);
 
                // Move locations into children
                for (int i = 0; i < cursor.locationCount; i++) {
                    double[] oldLocation = cursor.locations[i];
                    Object oldData = cursor.data[i];
                    if (oldLocation[cursor.splitDimension] > cursor.splitValue) {
                        // Right
                        right.locations[right.locationCount] = oldLocation;
                        right.data[right.locationCount] = oldData;
                        right.locationCount++;
                        right.extendBounds(oldLocation);
                    }
                    else
                    {
                        // Left
                        left.locations[left.locationCount] = oldLocation;
                        left.data[left.locationCount] = oldData;
                        left.locationCount++;
                        left.extendBounds(oldLocation);
                    }
                }
 
                // Make into stem
                cursor.left = left;
                cursor.right = right;
                cursor.locations = null;
                cursor.data = null;
            } //end of if
 
            cursor.locationCount++;
            cursor.extendBounds(location);
 
            if (location[cursor.splitDimension] > cursor.splitValue) {
                cursor = cursor.right;
            }
            else
            {
                cursor = cursor.left;
            }
        } //end of while loop
 
        cursor.locations[cursor.locationCount] = location;
        cursor.data[cursor.locationCount] = value;
        cursor.locationCount++;
        cursor.extendBounds(location);
 
    }
 
    /**
     * Extends the bounds of this node do include a new location
     */
    private final void extendBounds(double[] location) {
        if (minLimit == null) {
            minLimit = new double[dimensions];
            System.arraycopy(location, 0, minLimit, 0, dimensions);
            maxLimit = new double[dimensions];
            System.arraycopy(location, 0, maxLimit, 0, dimensions);
            return;
        }
 
        for (int i = 0; i < dimensions; i++) {
            if (Double.isNaN(location[i])) {
                minLimit[i] = Double.NaN;
                maxLimit[i] = Double.NaN;
                singularity = false;
            }
            else if (minLimit[i] > location[i]) {
                minLimit[i] = location[i];
                singularity = false;
            }
            else if (maxLimit[i] < location[i]) {
                maxLimit[i] = location[i];
                singularity = false;
            }
        }
    }
 
    /**
     * Find the widest axis of the bounds of this node
     */
    private final int findWidestAxis() {
        int widest = 0;
        double width = (maxLimit[0] - minLimit[0]) * getAxisWeightHint(0);
        if (Double.isNaN(width)) width = 0;
        for (int i = 1; i < dimensions; i++) {
            double nwidth = (maxLimit[i] - minLimit[i]) * getAxisWeightHint(i);
            if (Double.isNaN(nwidth)) nwidth = 0;
            if (nwidth > width) {
                widest = i;
                width = nwidth;
            }
        }
        return widest;
    }

    public List<KdTree<T>> getLeaves(){
    	List<KdTree<T>> list = new ArrayList<KdTree<T>>();
    	this.helper(list);
    	return list;
    }
    
    private void helper(List<KdTree<T>> list ){
    	if(this.left == null && this.right==null)
    		list.add(this);
    	else{
    		if(this.left != null)
    			this.left.helper(list);
    		if(this.right != null)
    			this.right.helper(list);
    	}
    }
    
    
 
    protected double pointDist(double[] p1, double[] p2) {
        double d = 0;

        for (int i = 0; i < p1.length; i++) {
            double diff = (p1[i] - p2[i]);
            if (!Double.isNaN(diff)) {
                d += diff * diff;
            }
        }

        return d;
    }
    
    protected double pointRegionDist(double[] point, double[] min, double[] max) {
        double d = 0;

        for (int i = 0; i < point.length; i++) {
            double diff = 0;
            if (point[i] > max[i]) {
                diff = (point[i] - max[i]);
            }
            else if (point[i] < min[i]) {
                diff = (point[i] - min[i]);
            }

            if (!Double.isNaN(diff)) {
                d += diff * diff;
            }
        }

        return d;
    }
    
    public String toString(){
        if (data == null)
            return "";

    	String ret="";
    	for(int i=0; i<this.locationCount;i++)
        {
    		ret += this.data[i] + " ";
        }
    	return ret;
    }
    
    protected double getAxisWeightHint(int i) {
        return 1.0;
    }
 
    /**
     * Internal class for child nodes
     */
    private class ChildNode extends KdTree<T> {
        private ChildNode(KdTree<T> parent, boolean right) {
            super(parent, right);
        }
 
        // Distance measurements are always called from the root node
        protected double pointDist(double[] p1, double[] p2) {
            throw new IllegalStateException();
        }
 
        protected double pointRegionDist(double[] point, double[] min, double[] max) {
            throw new IllegalStateException();
        }
    } 

    private static String darrayToString(double[] array) {
        String retval = "";
        for (int i = 0; i < array.length; i++) {
            retval += array[i] + " ";
        }
        return retval;
    }

    public static void main(String[] args) {
        KdTree<String> tree = new KdTree<String>(2);
        tree.addPoint(new double[] { 1.0, 1.0 }, "hello1");
        tree.addPoint(new double[] { 10.0, 2.0 }, "world2");
        tree.addPoint(new double[] { 3.0, 4.0 }, "earth3");
        tree.addPoint(new double[] { 1.0, 1.0 }, "hello2");
        tree.addPoint(new double[] { 6.0, 7.0 }, "earth4");
        tree.addPoint(new double[] { 16.0, 7.0 }, "earth5");
        tree.addPoint(new double[] { 1.0, 1.0 }, "hello3");
        tree.addPoint(new double[] { 1.0, 1.0 }, "hello4");
        
        System.out.println(tree);
        KdTree<String> leaf = tree.getLeaf(new double[] { 1.0, 1.0 });
        System.out.println(leaf);
        System.out.println(darrayToString(leaf.maxLimit));
        System.out.println(darrayToString(leaf.minLimit));
    }

}