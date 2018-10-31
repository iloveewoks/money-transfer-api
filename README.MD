# Money Transfer Api

## Packaging

To make a fat Jar of the app execute:

```sh
$ sbt assembly
```
Resulting Jar would be in the ****{project}*/target/scala-2.12/*** directory

## API
### Accounts
##### Create account
**POST** /accounts
###### Response:

```javascript
{
    "id": "8dab23c1-77b6-4863-85a4-9e2c714a1b33",
    "balance": 0
}
```

##### Get account
**GET** /accounts/*{uuid}*
###### Response:

```javascript
{
    "id": "8dab23c1-77b6-4863-85a4-9e2c714a1b33",
    "balance": 0
}
```

##### Get all accounts
**GET** /accounts
###### Response:

```javascript
[
    {
        "id": "34fe231a-aab4-4ab8-b253-0f9ec5b60697",
        "balance": 0
    },
    {
        "id": "8dab23c1-77b6-4863-85a4-9e2c714a1b33",
        "balance": 0
    },
    {
        "id": "a95b0a2c-9570-489c-b832-0f87b78dc8af",
        "balance": 0
    }
]
```

### Transactions
##### Deposit
**POST** /transactions/deposit
###### Body:

```javascript
{
	"to" : "8dab23c1-77b6-4863-85a4-9e2c714a1b33",
	"amount" : 200
}
```

###### Response:

```javascript
{
    "amount": 200,
    "to": "8dab23c1-77b6-4863-85a4-9e2c714a1b33",
    "id": "1b470af9-718e-4dfc-a572-c9fe12cf6cc3",
    "status": "COMPLETED",
    "type": "DEPOSIT",
    "date-time": "2018-10-31T02:43:45.427Z"
}
```
##### Withdrawal
**POST** /transactions/withdraw
###### Body:

```javascript
{
	"from" : "8dab23c1-77b6-4863-85a4-9e2c714a1b33",
	"amount" : 100
}
```

###### Response:

```javascript
{
    "amount": 100,
    "id": "81361f80-709e-477b-aa80-b5b5d2c62962",
    "status": "COMPLETED",
    "from": "8dab23c1-77b6-4863-85a4-9e2c714a1b33",
    "type": "WITHDRAWAL",
    "date-time": "2018-10-31T02:45:15.503Z"
}
```

##### Transfer
**POST** /transactions/transfer
###### Body:

```javascript
{
	"from" : "8dab23c1-77b6-4863-85a4-9e2c714a1b33",
	"to" : "34fe231a-aab4-4ab8-b253-0f9ec5b60697",
	"amount" : 100
}
```

###### Response:

```javascript
{
    "amount": 100,
    "to": "34fe231a-aab4-4ab8-b253-0f9ec5b60697",
    "id": "3a2b6eb0-cf2a-470e-bef4-13a68d4fdebc",
    "status": "COMPLETED",
    "from": "8dab23c1-77b6-4863-85a4-9e2c714a1b33",
    "type": "TRANSFER",
    "date-time": "2018-10-31T02:47:06.939Z"
}
```

##### Get transaction
**GET** /transactions/*{uuid}*
###### Response:

```javascript
{
    "amount": 100,
    "to": "34fe231a-aab4-4ab8-b253-0f9ec5b60697",
    "id": "3a2b6eb0-cf2a-470e-bef4-13a68d4fdebc",
    "status": "COMPLETED",
    "from": "8dab23c1-77b6-4863-85a4-9e2c714a1b33",
    "type": "TRANSFER",
    "date-time": "2018-10-31T02:47:06.939Z"
}
```

##### Get all transactions
**GET** /transactions
###### Response:

```javascript
[
    {
        "amount": 100,
        "to": "34fe231a-aab4-4ab8-b253-0f9ec5b60697",
        "id": "3a2b6eb0-cf2a-470e-bef4-13a68d4fdebc",
        "status": "COMPLETED",
        "from": "8dab23c1-77b6-4863-85a4-9e2c714a1b33",
        "type": "TRANSFER",
        "date-time": "2018-10-31T02:47:06.939Z"
    },
    {
        "amount": 100,
        "id": "81361f80-709e-477b-aa80-b5b5d2c62962",
        "status": "COMPLETED",
        "from": "8dab23c1-77b6-4863-85a4-9e2c714a1b33",
        "type": "WITHDRAWAL",
        "date-time": "2018-10-31T02:45:15.503Z"
    },
    {
        "amount": 200,
        "to": "8dab23c1-77b6-4863-85a4-9e2c714a1b33",
        "id": "1b470af9-718e-4dfc-a572-c9fe12cf6cc3",
        "status": "COMPLETED",
        "type": "DEPOSIT",
        "date-time": "2018-10-31T02:43:45.427Z"
    }
]
```
