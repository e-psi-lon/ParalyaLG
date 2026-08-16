package fr.paralya.bot.common

import kotlinx.coroutines.CancellationException

inline fun <reified E : Exception, T, R> T.runCatchingTypedException(block: T.() -> R) : Result<R> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: E) {
        Result.failure(e)
    }
}
inline fun <T, R> T.runCatchingException(block: T.() -> R): Result<R> =
    runCatchingTypedException<Exception, T, R>(block)

inline fun <reified E : Exception, R> runCatchingTypedException(block: () -> R): Result<R> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: E) {
    Result.failure(e)
}

inline fun <R> runCatchingException(block: () -> R): Result<R> = runCatchingTypedException<Exception, R>(block)
class NonExceptionFailedResultException(message: String) : ParalyaBotException(message)

fun <T> Result<T>.getExceptionOrNull(): Exception? = when (val throwable = exceptionOrNull()) {
    null -> null
    is Exception -> throwable
    else -> throw NonExceptionFailedResultException("Result is not a subclass of Exception: " +
            throwable::class.simpleName.orUnknownClass()
    )
}
