/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.net;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.test.builders.BlockChainBuilder;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.rpc.Simples.SimpleChannelManager;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class BlockSyncServiceTest {
    @Test
    public void sendBlockMessagesAndAddThemToBlockchain() {
        for (int i = 0; i < 50; i += 5) {
            Blockchain blockchain = BlockChainBuilder.ofSize(10 * i);
            BlockStore store = new BlockStore();
            BlockNodeInformation nodeInformation = new BlockNodeInformation();
            BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());
            Assert.assertEquals(10 * i, blockchain.getBestBlock().getNumber());

            List<Block> extendedChain = BlockGenerator.getBlockChain(blockchain.getBestBlock(), i);
            for (Block block : extendedChain) {
                blockSyncService.processBlock(null, block);
                Assert.assertEquals(block.getNumber(), blockchain.getBestBlock().getNumber());
                Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlock().getHash());
            }
        }
    }

    @Test
    public void sendBlockMessagesAndAddThemToBlockchainInReverseOrder() {
        for (int i = 1; i < 52; i += 5) {
            Blockchain blockchain = BlockChainBuilder.ofSize(10 * i);
            BlockStore store = new BlockStore();
            BlockNodeInformation nodeInformation = new BlockNodeInformation();
            BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());
            Assert.assertEquals(10 * i, blockchain.getBestBlock().getNumber());

            Block initialBestBlock = blockchain.getBestBlock();
            List<Block> extendedChain = BlockGenerator.getBlockChain(blockchain.getBestBlock(), i);
            Collections.reverse(extendedChain);
            for (int j = 0; j < extendedChain.size() - 1; j++) {
                Block block = extendedChain.get(j);
                blockSyncService.processBlock(null, block);
                // we don't have all the parents, so we wait to update the best chain
                Assert.assertEquals(initialBestBlock.getNumber(), blockchain.getBestBlock().getNumber());
                Assert.assertArrayEquals(initialBestBlock.getHash(), blockchain.getBestBlock().getHash());
            }

            // the chain is complete, we have a new best block
            Block closingBlock = extendedChain.get(extendedChain.size() - 1);
            Block newBestBlock = extendedChain.get(0);
            blockSyncService.processBlock(null, closingBlock);
            Assert.assertEquals(newBestBlock.getNumber(), blockchain.getBestBlock().getNumber());
            Assert.assertArrayEquals(newBestBlock.getHash(), blockchain.getBestBlock().getHash());
        }
    }

    @Test
    public void sendBlockMessageAndAddItToBlockchainWithCommonAncestors() {
        Blockchain blockchain = BlockChainBuilder.ofSize(10);
        BlockStore store = new BlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, SyncConfiguration.IMMEDIATE_FOR_TESTING, new SimpleChannelManager());

        Block initialBestBlock = blockchain.getBestBlock();
        Assert.assertEquals(10, initialBestBlock.getNumber());
        Block branchingPoint = blockchain.getBlockByNumber(7);

        List<Block> extendedChain = BlockGenerator.getBlockChain(branchingPoint, 10);
        int branchingNumber = (int) (initialBestBlock.getNumber() - branchingPoint.getNumber());
        for (int i = 0; i < branchingNumber; i++) {
            Block block = extendedChain.get(i);
            blockSyncService.processBlock(null, block);
            Assert.assertEquals(initialBestBlock.getNumber(), blockchain.getBestBlock().getNumber());
            Assert.assertArrayEquals(initialBestBlock.getHash(), blockchain.getBestBlock().getHash());
        }
        // we have just surpassed the best branch
        for (int i = branchingNumber; i < extendedChain.size(); i++) {
            Block newBestBlock = extendedChain.get(i);
            blockSyncService.processBlock(null, newBestBlock);
            Assert.assertEquals(newBestBlock.getNumber(), blockchain.getBestBlock().getNumber());
            Assert.assertArrayEquals(newBestBlock.getHash(), blockchain.getBestBlock().getHash());
        }
    }
}