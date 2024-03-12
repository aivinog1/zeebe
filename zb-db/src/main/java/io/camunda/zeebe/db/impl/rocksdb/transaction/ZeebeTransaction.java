/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import static io.camunda.zeebe.db.impl.rocksdb.transaction.RocksDbInternal.isRocksDbExceptionRecoverable;

import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.agrona.LangUtil;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.PerfContext;
import org.rocksdb.PerfLevel;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;

public class ZeebeTransaction implements ZeebeDbTransaction, AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeTransaction.class);

  private final long nativeHandle;
  private final TransactionRenovator transactionRenovator;

  private boolean inCurrentTransaction;
  private Transaction transaction;
  private final OptimisticTransactionDB optimisticTransactionDB;

  public ZeebeTransaction(
      final Transaction transaction, final TransactionRenovator transactionRenovator, final
      OptimisticTransactionDB optimisticTransactionDB) {
    this.optimisticTransactionDB = optimisticTransactionDB;
    this.transactionRenovator = transactionRenovator;
    this.transaction = transaction;
    try {
      nativeHandle = RocksDbInternal.nativeHandle.getLong(transaction);
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public void put(
      final long columnFamilyHandle,
      final byte[] key,
      final int keyLength,
      final byte[] value,
      final int valueLength)
      throws Exception {
    try {
      final int keyOffset = 0;
      final int valueOffset = 0;
      RocksDbInternal.putWithHandle.invokeExact(
          transaction,
          nativeHandle,
          key,
          keyOffset,
          keyLength,
          value,
          valueOffset,
          valueLength,
          columnFamilyHandle,
          false);
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public byte[] get(
      final long columnFamilyHandle,
      final long readOptionsHandle,
      final byte[] key,
      final int keyLength)
      throws Exception {
    final StopWatch getStopWatch = new StopWatch();
    final PerfContext perfContext = optimisticTransactionDB.getPerfContext();
    try {
      final int keyOffset = 0;
      getStopWatch.start();
      optimisticTransactionDB.setPerfLevel(PerfLevel.ENABLE_TIME_AND_CPU_TIME_EXCEPT_FOR_MUTEX);
      perfContext.reset();
      final byte[] getResult =
          (byte[])
              RocksDbInternal.getWithHandle.invokeExact(
                  transaction,
                  nativeHandle,
                  readOptionsHandle,
                  key,
                  keyOffset,
                  keyLength,
                  columnFamilyHandle);
      getStopWatch.stop();
      return getResult;
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
      return null; // unreachable
    } finally {
      if (getStopWatch.isRunning()) {
        getStopWatch.stop();
      }
      optimisticTransactionDB.setPerfLevel(PerfLevel.DISABLE);
      final double getStopWatchTotalTime = getStopWatch.getTotalTime(TimeUnit.MILLISECONDS);
      if (getStopWatchTotalTime > 1) {
        LOGGER.info("Get time: {}ms", getStopWatchTotalTime);
        LOGGER.info("Get transaction. GetFromMemtableTime: {}", perfContext.getFromMemtableTime());
        LOGGER.info("Get transaction. GetFromOutputFilesTime: {}", perfContext.getFromOutputFilesTime());
        LOGGER.info("Get transaction. SeekOnMemtableTime: {}", perfContext.getSeekOnMemtableTime());
      }
    }
  }

  public void delete(final long columnFamilyHandle, final byte[] key, final int keyLength)
      throws Exception {
    try {
      RocksDbInternal.removeWithHandle.invokeExact(
          transaction, nativeHandle, key, keyLength, columnFamilyHandle, false);
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public RocksIterator newIterator(final ReadOptions options, final ColumnFamilyHandle handle) {
    return transaction.getIterator(options, handle);
  }

  void resetTransaction() {
    transaction = transactionRenovator.renewTransaction(transaction);
    inCurrentTransaction = true;
  }

  boolean isInCurrentTransaction() {
    return inCurrentTransaction;
  }

  @Override
  public void run(final TransactionOperation operations) throws Exception {
    try {
      operations.run();
    } catch (final RocksDBException rdbex) {
      final String errorMessage = "Unexpected error occurred during RocksDB transaction commit.";
      if (isRocksDbExceptionRecoverable(rdbex)) {
        throw new ZeebeDbException(errorMessage, rdbex);
      }
      throw rdbex;
    }
  }

  @Override
  public void commit() throws RocksDBException {
    try {
      commitInternal();
    } catch (final RocksDBException rdbex) {
      final String errorMessage = "Unexpected error occurred during RocksDB transaction commit.";
      if (isRocksDbExceptionRecoverable(rdbex)) {
        throw new ZeebeDbException(errorMessage, rdbex);
      }
      throw rdbex;
    }
  }

  @Override
  public void rollback() throws RocksDBException {
    try {
      rollbackInternal();
    } catch (final RocksDBException rdbex) {
      final String errorMessage = "Unexpected error occurred during RocksDB transaction rollback.";
      if (isRocksDbExceptionRecoverable(rdbex)) {
        throw new ZeebeDbException(errorMessage, rdbex);
      }
      throw rdbex;
    }
  }

  void commitInternal() throws RocksDBException {
    inCurrentTransaction = false;
    transaction.commit();
  }

  void rollbackInternal() throws RocksDBException {
    inCurrentTransaction = false;
    transaction.rollback();
  }

  @Override
  public void close() {
    transaction.close();
  }
}
