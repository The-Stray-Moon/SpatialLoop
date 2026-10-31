package cc.sighs.spatialloop.world;

/**
 * 由 {@code DistanceManager} 的 mixin 实现：限制模拟距离，避免相距一个周期的两块区块同时处于
 * 模拟（ENTITY_TICKING）状态。
 *
 * <p>视距已经被压到 {@code 半径 - 1}，但模拟距离（默认 10）可能大于周期的一半，小半径下会让
 * 同一块空间的两份副本同时加载并各自保存，产生「谁后保存谁覆盖」的问题。把模拟距离压到
 * {@code 2r - 1} 以下即可保证任何两块同余区块不会同时处于完整状态。
 */
public interface LoopSimulation {
    int spatialloop$simulationRadius();

    void spatialloop$setSimulationRadius(int radius);
}
