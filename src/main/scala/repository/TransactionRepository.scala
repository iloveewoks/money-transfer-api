package repository

import model.TransactionInfo

abstract class TransactionRepository extends InMemoryRepository[TransactionInfo] {

}

class TransactionInMemoryRepository extends TransactionRepository {

}
