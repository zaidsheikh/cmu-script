package edu.cmu.cs.lti.utils;

import edu.cmu.cs.lti.collections.TLongShortDoubleHashTable;
import gnu.trove.map.hash.TObjectShortHashMap;

/**
 * Created with IntelliJ IDEA.
 * User: zhengzhongliu
 * Date: 11/11/14
 * Time: 3:49 PM
 */
public class TLongBasedFeatureTable {
    /**
     * Long is the main key, which can encode two integers (then map to two words)
     * <p/>
     * Int is the secondary key, which point to next specific feature, maintained
     * in a separate feature lookup table, which should be pretty small,do not
     * populate a lot of word features over there
     * <p/>
     * Double is the feature value to be update
     */
//    TLongObjectHashMap<TShortDoubleMap> table = new TLongObjectHashMap<>();

    TLongShortDoubleHashTable table = new TLongShortDoubleHashTable();

    TObjectShortHashMap<String> secondaryFeatureLookupMap = new TObjectShortHashMap<>();

    short nextKey = Short.MIN_VALUE;

    public TLongBasedFeatureTable() {

    }

    public int getNumRows() {
        return table.getNumRows();
    }

    /**
     * Can use both negative and positive section of short to store value
     *
     * @param featureName
     * @return
     */
    public short getOrPutFeatureIndex(String featureName) {
        if (secondaryFeatureLookupMap.containsKey(featureName)) {
            return secondaryFeatureLookupMap.get(featureName);
        } else {
            secondaryFeatureLookupMap.put(featureName, nextKey);
            short currentKey = nextKey;
            nextKey++;
            if (nextKey == Short.MIN_VALUE) {
                //this will only happen when we circuit around
                throw new IllegalStateException("You have used up all shorts for features!");
            }
            return currentKey;
        }
    }

    /**
     * The data are stored in primitive, returning an object for easy null-check
     *
     * @param rowKey
     * @param colKey
     * @return return the value as Double object
     */
    public Double get(long rowKey, short colKey) {
        return table.get(rowKey, colKey);
    }

    public void put(long rowKey, short colKey, double value) {
        table.put(rowKey, colKey, value);
    }

    public boolean contains(long rowKey, short colKey) {
        return table.contains(rowKey, colKey);
    }

    public boolean adjust(long rowKey, short colKey, double value) {
        return table.adjust(rowKey, colKey, value);
    }

    /**
     * Adjusts the primitive value mapped to the key if the key pair is present in the map. Otherwise, the initial_value is put in the map.
     *
     * @param rowKey       the row key of the value to increment
     * @param colKey       the column key of the value to increment
     * @param adjustAmount the amount to increment the value by
     * @param putAmount    the value put into the map if the key is not initial present
     * @return the value present in the map after the adjustment or put operation
     */
    public double adjustOrPutValue(long rowKey, short colKey, double adjustAmount, double putAmount) {
        return table.adjustOrPutValue(rowKey, colKey, adjustAmount, putAmount);
    }

    public double dotProd(TLongShortDoubleHashTable features) {
        return table.dotProd(features);
    }

}