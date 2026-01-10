package com.sajid._207017_chashi_bhai.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * OrderService - centralizes order state transitions and related side-effects.
 *
 * Rules implemented:
 * - Only farmer can accept/reject/mark in-transit.
 * - Only buyer can cancel/mark received.
 * - Status cannot be changed after completed.
 * - Accept deducts crop available quantity.
 * - Cancel/Delete restores crop quantity if it was previously deducted.
 */
public final class OrderService {

    private OrderService() {}

    public enum Action {
        ACCEPT,
        REJECT,
        MARK_IN_TRANSIT,
        MARK_RECEIVED,
        CANCEL,
        DELETE
    }

    public static final class ActionResult {
        public final boolean ok;
        public final String message;

        private ActionResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        public static ActionResult ok(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult fail(String message) {
            return new ActionResult(false, message);
        }
    }

    public static void acceptOrderAsync(int orderId, int actorUserId,
                                       java.util.function.Consumer<ActionResult> onSuccess,
                                       java.util.function.Consumer<Exception> onError) {
        transitionAsync(orderId, actorUserId, Action.ACCEPT, onSuccess, onError);
    }

    public static void rejectOrderAsync(int orderId, int actorUserId,
                                       java.util.function.Consumer<ActionResult> onSuccess,
                                       java.util.function.Consumer<Exception> onError) {
        transitionAsync(orderId, actorUserId, Action.REJECT, onSuccess, onError);
    }

    public static void markInTransitAsync(int orderId, int actorUserId,
                                         java.util.function.Consumer<ActionResult> onSuccess,
                                         java.util.function.Consumer<Exception> onError) {
        transitionAsync(orderId, actorUserId, Action.MARK_IN_TRANSIT, onSuccess, onError);
    }

    public static void markReceivedAsync(int orderId, int actorUserId,
                                        java.util.function.Consumer<ActionResult> onSuccess,
                                        java.util.function.Consumer<Exception> onError) {
        transitionAsync(orderId, actorUserId, Action.MARK_RECEIVED, onSuccess, onError);
    }

    public static void cancelOrderAsync(int orderId, int actorUserId,
                                       java.util.function.Consumer<ActionResult> onSuccess,
                                       java.util.function.Consumer<Exception> onError) {
        transitionAsync(orderId, actorUserId, Action.CANCEL, onSuccess, onError);
    }

    public static void deleteOrderAsync(int orderId, int actorUserId,
                                       java.util.function.Consumer<ActionResult> onSuccess,
                                       java.util.function.Consumer<Exception> onError) {
        transitionAsync(orderId, actorUserId, Action.DELETE, onSuccess, onError);
    }

    private static void transitionAsync(int orderId, int actorUserId, Action action,
                                        java.util.function.Consumer<ActionResult> onSuccess,
                                        java.util.function.Consumer<Exception> onError) {
        DatabaseService.executeTransactionAsync(conn -> {
            OrderRow order = fetchOrder(conn, orderId);
            if (order == null) {
                return ActionResult.fail("অর্ডার খুঁজে পাওয়া যায়নি।");
            }

            if ("completed".equals(order.status)) {
                return ActionResult.fail("এই অর্ডারটি সম্পন্ন হয়েছে—আর পরিবর্তন করা যাবে না।");
            }

            return switch (action) {
                case ACCEPT -> doAccept(conn, order, actorUserId);
                case REJECT -> doReject(conn, order, actorUserId);
                case MARK_IN_TRANSIT -> doInTransit(conn, order, actorUserId);
                case MARK_RECEIVED -> doCompleted(conn, order, actorUserId);
                case CANCEL -> doCancel(conn, order, actorUserId);
                case DELETE -> doDelete(conn, order, actorUserId);
            };
        },
        result -> {
            if (onSuccess != null) onSuccess.accept(result);
        },
        onError);
    }

    private static ActionResult doAccept(Connection conn, OrderRow order, int actorUserId) throws Exception {
        if (actorUserId != order.farmerId) {
            return ActionResult.fail("শুধুমাত্র এই অর্ডারের কৃষক গ্রহণ করতে পারবেন।");
        }
        if (!"new".equals(order.status)) {
            return ActionResult.fail("শুধুমাত্র নতুন অর্ডার গ্রহণ করা যাবে।");
        }

        CropRow crop = fetchCrop(conn, order.cropId);
        if (crop == null) {
            return ActionResult.fail("ফসল খুঁজে পাওয়া যায়নি।");
        }
        if (crop.availableQty < order.quantityKg) {
            return ActionResult.fail("পর্যাপ্ত পরিমাণ নেই। Available: " + crop.availableQty + " কেজি");
        }

        // Deduct first (guarded), then mark order accepted. If marking fails, restore.
        int cropRows;
        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE crops " +
            "SET available_quantity_kg = available_quantity_kg - ?, " +
            "    status = CASE WHEN (available_quantity_kg - ?) <= 0 THEN 'sold' ELSE status END, " +
            "    updated_at = datetime('now') " +
            "WHERE id = ? AND available_quantity_kg >= ?")) {
            ps.setDouble(1, order.quantityKg);
            ps.setDouble(2, order.quantityKg);
            ps.setInt(3, order.cropId);
            ps.setDouble(4, order.quantityKg);
            cropRows = ps.executeUpdate();
        }
        if (cropRows <= 0) {
            return ActionResult.fail("পর্যাপ্ত পরিমাণ নেই। Available: " + crop.availableQty + " কেজি");
        }

        int orderRows;
        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE orders SET status = 'accepted', accepted_at = datetime('now'), updated_at = datetime('now') " +
            "WHERE id = ? AND status = 'new' AND farmer_id = ?")) {
            ps.setInt(1, order.id);
            ps.setInt(2, actorUserId);
            orderRows = ps.executeUpdate();
        }
        if (orderRows <= 0) {
            // restore crop because order accept didn't go through
            try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE crops SET available_quantity_kg = available_quantity_kg + ?, status = 'active', updated_at = datetime('now') WHERE id = ?")) {
                ps.setDouble(1, order.quantityKg);
                ps.setInt(2, order.cropId);
                ps.executeUpdate();
            }
            return ActionResult.fail("অর্ডার গ্রহণ করতে ব্যর্থ হয়েছে।");
        }

        return ActionResult.ok("✅ অর্ডার গৃহীত হয়েছে!");
    }

    private static ActionResult doReject(Connection conn, OrderRow order, int actorUserId) throws Exception {
        if (actorUserId != order.farmerId) {
            return ActionResult.fail("শুধুমাত্র এই অর্ডারের কৃষক প্রত্যাখ্যান করতে পারবেন।");
        }
        if (!"new".equals(order.status)) {
            return ActionResult.fail("শুধুমাত্র নতুন অর্ডার প্রত্যাখ্যান করা যাবে।");
        }

        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE orders SET status = 'rejected', updated_at = datetime('now') WHERE id = ? AND status = 'new'")) {
            ps.setInt(1, order.id);
            int rows = ps.executeUpdate();
            if (rows <= 0) {
                return ActionResult.fail("অর্ডার প্রত্যাখ্যান করতে ব্যর্থ হয়েছে।");
            }
        }

        return ActionResult.ok("❌ অর্ডার প্রত্যাখ্যাত হয়েছে।");
    }

    private static ActionResult doInTransit(Connection conn, OrderRow order, int actorUserId) throws Exception {
        if (actorUserId != order.farmerId) {
            return ActionResult.fail("শুধুমাত্র এই অর্ডারের কৃষক ডেলিভারির জন্য দিতে পারবেন।");
        }
        if (!"accepted".equals(order.status)) {
            return ActionResult.fail("শুধুমাত্র গৃহীত অর্ডার ডেলিভারির জন্য দেওয়া যাবে।");
        }

        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE orders SET status = 'in_transit', in_transit_at = datetime('now'), updated_at = datetime('now') WHERE id = ? AND status = 'accepted'")) {
            ps.setInt(1, order.id);
            int rows = ps.executeUpdate();
            if (rows <= 0) {
                return ActionResult.fail("স্ট্যাটাস আপডেট করতে ব্যর্থ হয়েছে।");
            }
        }

        return ActionResult.ok("🚚 ডেলিভারির জন্য পাঠানো হয়েছে!");
    }

    private static ActionResult doCompleted(Connection conn, OrderRow order, int actorUserId) throws Exception {
        if (actorUserId != order.buyerId) {
            return ActionResult.fail("শুধুমাত্র এই অর্ডারের ক্রেতা পণ্য গ্রহণ নিশ্চিত করতে পারবেন।");
        }
        if (!"in_transit".equals(order.status) && !"delivered".equals(order.status)) {
            return ActionResult.fail("শুধুমাত্র ডেলিভারির পথে থাকা অর্ডার সম্পন্ন করা যাবে।");
        }

        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE orders SET status = 'completed', completed_at = datetime('now'), updated_at = datetime('now') WHERE id = ? AND status IN ('in_transit','delivered')")) {
            ps.setInt(1, order.id);
            int rows = ps.executeUpdate();
            if (rows <= 0) {
                return ActionResult.fail("স্ট্যাটাস আপডেট করতে ব্যর্থ হয়েছে।");
            }
        }

        return ActionResult.ok("✅ পণ্য গ্রহণ নিশ্চিত করা হয়েছে!");
    }

    private static ActionResult doCancel(Connection conn, OrderRow order, int actorUserId) throws Exception {
        if (actorUserId != order.buyerId) {
            return ActionResult.fail("শুধুমাত্র এই অর্ডারের ক্রেতা বাতিল করতে পারবেন।");
        }
        if ("in_transit".equals(order.status) || "delivered".equals(order.status)) {
            return ActionResult.fail("ডেলিভারির পর অর্ডার বাতিল করা যাবে না।");
        }
        if (!"new".equals(order.status) && !"accepted".equals(order.status)) {
            return ActionResult.fail("এই স্ট্যাটাসে অর্ডার বাতিল করা যাবে না।");
        }

        int rows;
        if ("accepted".equals(order.status)) {
            try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE orders SET status = 'cancelled', updated_at = datetime('now') WHERE id = ? AND status = 'accepted' AND buyer_id = ?")) {
                ps.setInt(1, order.id);
                ps.setInt(2, actorUserId);
                rows = ps.executeUpdate();
            }
            if (rows <= 0) {
                return ActionResult.fail("অর্ডার বাতিল করতে ব্যর্থ হয়েছে।");
            }
            restoreCropQuantity(conn, order);
        } else {
            try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE orders SET status = 'cancelled', updated_at = datetime('now') WHERE id = ? AND status = 'new' AND buyer_id = ?")) {
                ps.setInt(1, order.id);
                ps.setInt(2, actorUserId);
                rows = ps.executeUpdate();
            }
            if (rows <= 0) {
                return ActionResult.fail("অর্ডার বাতিল করতে ব্যর্থ হয়েছে।");
            }
        }

        return ActionResult.ok("❌ অর্ডার বাতিল হয়েছে।");
    }

    private static ActionResult doDelete(Connection conn, OrderRow order, int actorUserId) throws Exception {
        if (actorUserId != order.buyerId && actorUserId != order.farmerId) {
            return ActionResult.fail("শুধুমাত্র এই অর্ডারের মালিক ডিলিট করতে পারবেন।");
        }
        if ("in_transit".equals(order.status) || "delivered".equals(order.status)) {
            return ActionResult.fail("ডেলিভারির পর অর্ডার ডিলিট করা যাবে না।");
        }

        boolean restored = false;
        if ("accepted".equals(order.status)) {
            restoreCropQuantity(conn, order);
            restored = true;
        }

        int rows;
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM orders WHERE id = ?")) {
            ps.setInt(1, order.id);
            rows = ps.executeUpdate();
        }
        if (rows <= 0) {
            if (restored) {
                // revert restore (best-effort)
                try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE crops SET available_quantity_kg = available_quantity_kg - ?, updated_at = datetime('now') WHERE id = ? AND available_quantity_kg >= ?")) {
                    ps.setDouble(1, order.quantityKg);
                    ps.setInt(2, order.cropId);
                    ps.setDouble(3, order.quantityKg);
                    ps.executeUpdate();
                }
            }
            return ActionResult.fail("অর্ডার ডিলিট করতে ব্যর্থ হয়েছে।");
        }

        return ActionResult.ok("🗑 অর্ডার ডিলিট হয়েছে।");
    }

    private static void restoreCropQuantity(Connection conn, OrderRow order) throws Exception {
        CropRow crop = fetchCrop(conn, order.cropId);
        if (crop == null) {
            return;
        }
        double restored = crop.availableQty + order.quantityKg;
        if (crop.initialQty > 0) {
            restored = Math.min(crop.initialQty, restored);
        }

        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE crops SET available_quantity_kg = ?, status = 'active', updated_at = datetime('now') WHERE id = ?")) {
            ps.setDouble(1, restored);
            ps.setInt(2, order.cropId);
            ps.executeUpdate();
        }
    }

    private static OrderRow fetchOrder(Connection conn, int orderId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT id, crop_id, farmer_id, buyer_id, quantity_kg, status FROM orders WHERE id = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new OrderRow(
                    rs.getInt("id"),
                    rs.getInt("crop_id"),
                    rs.getInt("farmer_id"),
                    rs.getInt("buyer_id"),
                    rs.getDouble("quantity_kg"),
                    rs.getString("status")
                );
            }
        }
    }

    private static CropRow fetchCrop(Connection conn, int cropId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT id, available_quantity_kg, initial_quantity_kg FROM crops WHERE id = ?")) {
            ps.setInt(1, cropId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new CropRow(
                    rs.getInt("id"),
                    rs.getDouble("available_quantity_kg"),
                    rs.getDouble("initial_quantity_kg")
                );
            }
        }
    }

    private static final class OrderRow {
        final int id;
        final int cropId;
        final int farmerId;
        final int buyerId;
        final double quantityKg;
        final String status;

        private OrderRow(int id, int cropId, int farmerId, int buyerId, double quantityKg, String status) {
            this.id = id;
            this.cropId = cropId;
            this.farmerId = farmerId;
            this.buyerId = buyerId;
            this.quantityKg = quantityKg;
            this.status = status;
        }
    }

    private static final class CropRow {
        final int id;
        final double availableQty;
        final double initialQty;

        private CropRow(int id, double availableQty, double initialQty) {
            this.id = id;
            this.availableQty = availableQty;
            this.initialQty = initialQty;
        }
    }
}
