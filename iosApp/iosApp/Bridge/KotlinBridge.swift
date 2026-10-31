import Foundation
import Shared

/// Ties Kotlin flow subscriptions and view models to the lifetime of one
/// Swift model: everything is cancelled when the model is deallocated.
final class KotlinScope {
    private var subscriptions: [FlowSubscription] = []
    private var owners: [ViewModelOwner] = []

    /// Keeps a Kotlin view model alive until this scope goes away, then runs its onCleared().
    func own(_ viewModel: Lifecycle_viewmodelViewModel) {
        owners.append(ViewModelOwner(viewModel: viewModel))
    }

    /// Delivers every value of [flow] on the main thread; StateFlows deliver their current value immediately.
    func watch(_ flow: any Kotlinx_coroutines_coreFlow, _ onEach: @escaping (Any?) -> Void) {
        subscriptions.append(IosServicesKt.watch(flow: flow, onEach: onEach))
    }

    deinit {
        subscriptions.forEach { $0.cancel() }
        owners.forEach { $0.clear() }
    }
}

// MARK: - Unboxing Kotlin values

/// Kotlin collections cross over as Foundation types with boxed numbers; these
/// helpers turn flow values back into plain Swift ones.
enum Unbox {
    static func bool(_ value: Any?) -> Bool {
        (value as? NSNumber)?.boolValue ?? false
    }

    static func int64(_ value: Any?) -> Int64 {
        (value as? NSNumber)?.int64Value ?? 0
    }

    static func list<T>(_ value: Any?) -> [T] {
        (value as? [T]) ?? []
    }

    static func strings(_ value: Any?) -> Set<String> {
        if let set = value as? Set<String> { return set }
        return Set((value as? NSSet)?.compactMap { $0 as? String } ?? [])
    }

    /// A Kotlin `Map<Long, V>`.
    static func longKeyed<V>(_ value: Any?) -> [Int64: V] {
        guard let dict = value as? NSDictionary else { return [:] }
        var result: [Int64: V] = [:]
        for (key, item) in dict {
            if let key = key as? NSNumber, let item = item as? V {
                result[key.int64Value] = item
            }
        }
        return result
    }

    /// A Kotlin `Map<String, V>`.
    static func stringKeyed<V>(_ value: Any?) -> [String: V] {
        (value as? [String: V]) ?? [:]
    }
}

extension Optional where Wrapped == Int64 {
    /// For Kotlin parameters typed `Long?`.
    var kotlin: KotlinLong? { map { KotlinLong(value: $0) } }
}
