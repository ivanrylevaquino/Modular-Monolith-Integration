package edu.cit.aquino.channel;

class TianggeContext {
    private static final ThreadLocal<Boolean> IS_TIANGGE = ThreadLocal.withInitial(() -> false);

    static void set(boolean isTiangge) {
        IS_TIANGGE.set(isTiangge);
    }

    static boolean isTiangge() {
        return Boolean.TRUE.equals(IS_TIANGGE.get());
    }

    static void clear() {
        IS_TIANGGE.remove();
    }
}
