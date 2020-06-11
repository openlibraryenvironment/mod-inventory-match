package org.folio.inventorymatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class InventoryRecordSet {

    private JsonObject sourceJson = null;
    private JsonObject instanceJson = null;
    private Instance instance = null;
    private Map<String,HoldingsRecord> holdingsRecordsByHRID     = new HashMap<String,HoldingsRecord>();
    private Map<String,Item> itemsByHRID = new HashMap<String,Item>();
    private final Logger logger = LoggerFactory.getLogger("inventory-matcher");

    public InventoryRecordSet (JsonObject inventoryRecordSet) {
        if (inventoryRecordSet != null) {
            sourceJson = new JsonObject(inventoryRecordSet.toString());
            instanceJson = inventoryRecordSet.getJsonObject("instance");
            instance = new Instance(instanceJson);
            registerHoldingsRecordsAndItems (inventoryRecordSet.getJsonArray("holdingsRecords"));
        }
    }

    private void registerHoldingsRecordsAndItems (JsonArray holdingsRecordsWithEmbeddedItems) {
        if (holdingsRecordsWithEmbeddedItems != null) {
            for (Object holdings : holdingsRecordsWithEmbeddedItems) {
                JsonObject holdingsRecordJson = (JsonObject) holdings;
                JsonArray items = new JsonArray();
                if (holdingsRecordJson.containsKey("items")) {
                    items = extractJsonArrayFromObject(holdingsRecordJson, "items");
                }
                HoldingsRecord holdingsRecord = new HoldingsRecord(holdingsRecordJson);
                for (Object object : items) {
                    JsonObject itemJson = (JsonObject) object;
                    Item item = new Item(itemJson);
                    itemsByHRID.put(itemJson.getString("hrid"), item);
                    holdingsRecord.addItem(item);
                }
                holdingsRecordsByHRID.put(holdingsRecordJson.getString("hrid"), holdingsRecord);
                instance.addHoldingsRecord(holdingsRecord);
            }
        }
    }


    public JsonObject getSourceJson() {
        return sourceJson;
    }

    public String getInstanceHRID () {
        if (instanceJson == null) {
            return "no instance - no hrid";
        } else {
            return instanceJson.getString("hrid");
        }
    }

    public String getInstanceUUID () {
        if (instanceJson == null) {
            return "no instance - no UUID";
        } else {
            return instanceJson.getString("id");
        }
    }

    public HoldingsRecord getHoldingsRecordByHRID (String holdingsHrid) {
        return holdingsRecordsByHRID.get(holdingsHrid);
    }

    public Item getItemByHRID (String itemHrid) {
        return itemsByHRID.get(itemHrid);
    }

    public Instance getInstance () {
        return instance;
    }

    public Map<String, HoldingsRecord> getMapOfHoldingsRecordsByHRID () {
        return holdingsRecordsByHRID;
    }

    public Map<String, Item> getMapOfItemsByHRID() {
        return itemsByHRID;
    }

    public List<Item> getItemsByTransitionType (Transition transition) {
        List<Item> records = new ArrayList<Item>();
        Collection<Item> allRecords = itemsByHRID.values();
        Iterator<Item> recordsIterator = allRecords.iterator();
        while (recordsIterator.hasNext()) {
            Item record = recordsIterator.next();
            if (record.getTransition() == transition) {
                records.add(record);
            }
        }
        return records;
    }

    public List<HoldingsRecord> getHoldingsRecordsByTransitionType (Transition transition) {
        List<HoldingsRecord> records = new ArrayList<HoldingsRecord>();
        Collection<HoldingsRecord> allRecords = holdingsRecordsByHRID.values();
        Iterator<HoldingsRecord> recordsIterator = allRecords.iterator();
        while (recordsIterator.hasNext()) {
            HoldingsRecord record = recordsIterator.next();
            if (record.getTransition() == transition) {
                records.add(record);
            }
        }
        return records;
    }

    /**
     * Creates a deep clone of a JSONArray from a JSONObject, removes the array from the source object and returns the clone
     * @param jsonObject Source object containing the array to extract
     * @param arrayName Property name of the array to extract
     * @return  The extracted JsonArray or an empty JsonArray if none found to extract.
     */
    private static JsonArray extractJsonArrayFromObject(JsonObject jsonObject, String arrayName)  {
        JsonArray array = new JsonArray();
        if (jsonObject.containsKey(arrayName)) {
            array = new JsonArray((jsonObject.getJsonArray(arrayName)).encode());
            jsonObject.remove(arrayName);
        }
        return array;
    }

    public enum Transition {
        UNKNOWN,
        CREATING,
        UPDATING,
        DELETING
    }

    public abstract class InventoryRecord {
        protected JsonObject jsonRecord;
        protected Transition transition = Transition.UNKNOWN;
        public void setTransition (Transition transition) {
            this.transition = transition;
        }

        public Transition getTransition () {
            return transition;
        }

        public boolean isDeleting () {
            return (transition == Transition.DELETING);
        }

        public boolean isUpdating () {
            return (transition == Transition.UPDATING);
        }

        public boolean isCreating () {
            return (transition == Transition.CREATING);
        }

        public boolean stateUnknown () {
            return (transition == Transition.UNKNOWN);
        }

        public String generateUUID () {
            UUID uuid = UUID.randomUUID();
            jsonRecord.put("id", uuid.toString());
            return uuid.toString();
        }

        public void setUUID (String uuid) {
            jsonRecord.put("id", uuid);
        }

        public String getUUID () {
            return jsonRecord.getString("id");
        }

        public boolean hasUUID () {
            return (jsonRecord.getString("id") != null);
        }

        public String getHRID () {
            return jsonRecord.getString("hrid");
        }

        public JsonObject getJson() {
            return jsonRecord;
        }

    }

    public class Instance extends InventoryRecord {
        List<HoldingsRecord> holdingsRecords = new ArrayList<HoldingsRecord>();

        public Instance (JsonObject instance) {
            jsonRecord = instance;
        }

        public void setUUID(String uuid) {
            super.setUUID(uuid);
            setHoldingsRecordsInstanceId(uuid);
        }

        public String generateUUID () {
            String uuid = super.generateUUID();
            setHoldingsRecordsInstanceId(uuid);
            return uuid;
        }

        public void setHoldingsRecordsInstanceId (String uuid) {
            for (HoldingsRecord record : holdingsRecords) {
                record.setInstanceId(uuid);
            }
        }

        public void addHoldingsRecord(HoldingsRecord holdingsRecord) {
            if (hasUUID() && ! holdingsRecord.hasInstanceId()) {
                holdingsRecord.setInstanceId(getUUID());
            }
            holdingsRecords.add(holdingsRecord);
        }

        public List<HoldingsRecord> getHoldingsRecords() {
            return holdingsRecords;
        }

        public HoldingsRecord getHoldingsRecordByHRID (String hrid) {
            for (int i=0; i<holdingsRecords.size(); i++) {
                if (holdingsRecords.get(i).getHRID().equals(hrid)) {
                    return holdingsRecords.get(i);
                }
            }
            return null;
        }
    }

    public class HoldingsRecord extends InventoryRecord {

        List<Item> items = new ArrayList<Item>();

        public HoldingsRecord(JsonObject holdingsRecord) {
            this.jsonRecord = holdingsRecord;
        }

        public void setUUID (String uuid) {
            super.setUUID(uuid);
            setItemsHoldingsRecordId(uuid);
        }

        public String generateUUID () {
            String uuid = super.generateUUID();
            setItemsHoldingsRecordId(uuid);
            return uuid;
        }

        public void setItemsHoldingsRecordId (String uuid) {
            for (Item record : items) {
                record.setHoldingsRecordId(uuid);
            }
        }

        public String getInstanceId () {
            return jsonRecord.getString("instanceId");
        }

        public boolean hasInstanceId () {
            return (getInstanceId() != null);
        }

        public void setInstanceId (String uuid) {
            jsonRecord.put("instanceId", uuid);
        }

        public void addItem(Item item) {
            items.add(item);
            if (hasUUID() && ! item.hasHoldingsRecordId()) {
                item.setHoldingsRecordId(getUUID());
            }
        }

        public List<Item> getItems() {
            return items;
        }

        public Item getItemByHRID (String hrid) {
            for (int i=0; i<items.size(); i++) {
                if (items.get(i).getHRID().equals(hrid)) {
                    return items.get(i);
                }
            }
            return null;
        }
    }

    public class Item extends InventoryRecord {

        public Item (JsonObject item) {
            this.jsonRecord = item;
        }

        public String getHoldingsRecordId () {
            return jsonRecord.getString("holdingsRecordId");
        }

        public boolean hasHoldingsRecordId () {
            return (getHoldingsRecordId() != null);
        }

        public void setHoldingsRecordId (String uuid) {
            jsonRecord.put("holdingsRecordId", uuid);
        }

    }

}