package world.soapboxrace.mp.server;

import world.soapboxrace.mp.util.ArrayReader;
import world.soapboxrace.mp.util.ServerLog;

import java.nio.ByteBuffer;
import java.util.Objects;

public class SbrwParser implements IParser
{
    private static final byte ID_PLAYER_INFO = 0x02;
    private static final byte ID_CAR_STATE = 0x12;

    // full packet
    // 01:00:00:73:00:01:ff:ff:ff:ff:02:4a:00:50:4c:41:59:45:52:31:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:64:00:00:00:00:00:00:00:72:67:90:a3:e2:ba:08:00:21:00:00:00:fa:91:32:00:c0:2f:92:22:50:03:00:00:00:b0:79:e6:cf:ee:1e:9c:fb:12:1a:ba:ef:98:08:73:de:d1:a5:97:49:c4:25:89:c4:1f:1e:fb:f1:d3:96:96:96:9a:fc:00:1f:ff:b0:8d:c3:30:ff:

    // 01:00:00:73:00:01:ff:ff:ff:ff:
    private byte[] header;

    // 02:4a:00:50:4c:41:59:45:52:31:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:64:00:00:00:00:00:00:00:72:67:90:a3:e2:ba:08:00:21:00:00:00:fa:91:32:00:c0:2f:92:22:50:03:00:00:00:b0:79:e6:cf:ee:1e:9c:fb:
    private byte[] playerInfo;

    // 12:1a:ba:ef:98:08:73:de:d1:a5:97:49:c4:25:89:c4:1f:1e:fb:f1:d3:96:96:96:9a:fc:00:1f:
    private byte[] carState;

    private static final byte[] CRC_BYTES = {0x01, 0x02, 0x03, 0x04};

    @Override
    public void parse(byte[] packet)
    {
        // should always be false unless the client sends a corrupted packet
        if (packet.length < 16)
        {
            ServerLog.SERVER_LOGGER.error("Packet is too small ({} bytes, required at least 16)", packet.length);
            return;
        }

        ArrayReader arrayReader = new ArrayReader(packet);

        this.header = arrayReader.readBytes(10);

        while (arrayReader.getPosition() < arrayReader.getLength())
        {
            byte packetId = arrayReader.readByte();

            if (packetId == (byte) 0xff)
            {
                break;
            }

            byte packetLength = arrayReader.readByte();

            if (arrayReader.getPosition() + packetLength > arrayReader.getLength())
            {
                throw new IllegalStateException(String.format("Cannot read packet 0x%02x (0x%02x bytes, position %d, length %d)",
                        packetId,
                        packetLength,
                        arrayReader.getPosition(),
                        arrayReader.getLength()));
            }

            ServerLog.SERVER_LOGGER.debug("Packet - ID: {} | size: {}", String.format("0x%02x", packetId), String.format("0x%02x", packetLength));

            switch (packetId)
            {
                case ID_PLAYER_INFO:
                {
                    playerInfo = new byte[packetLength + 2];
                    playerInfo[0] = packetId;
                    playerInfo[1] = packetLength;

                    System.arraycopy(arrayReader.readBytes(packetLength), 0, playerInfo, 2, packetLength);

                    break;
                }
                case ID_CAR_STATE:
                {
                    carState = new byte[packetLength + 2];
                    carState[0] = packetId;
                    carState[1] = packetLength;

                    System.arraycopy(arrayReader.readBytes(packetLength), 0, carState, 2, packetLength);
                    break;
                }
                default:
                    ServerLog.SERVER_LOGGER.debug("Skipping packet");
                    arrayReader.seek(packetLength, true);
                    break;
            }
        }

        arrayReader.seek(arrayReader.getLength() - 4);
    }

    @Override
    public boolean isOk()
    {
        return isPlayerInfoOk() && isCarStateOk();
    }

    @Override
    public boolean isPlayerInfoOk()
    {
        return playerInfo != null;
    }

    @Override
    public byte[] getPlayerPacket(long timeDiff)
    {
        if (isOk())
        {
            byte[] statePosPacket = getStatePosPacket(timeDiff);
            int bufferSize = header.length + CRC_BYTES.length;

            if (playerInfo != null)
            {
                bufferSize += playerInfo.length;
            }

            if (statePosPacket != null)
            {
                bufferSize += statePosPacket.length;
            }

            ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);

            byteBuffer.put(header);

            if (playerInfo != null)
            {
                byteBuffer.put(playerInfo);
            }

            if (statePosPacket != null)
            {
                byteBuffer.put(statePosPacket);
            }

            byteBuffer.put(CRC_BYTES);

            byte[] array = byteBuffer.array();
            statePosPacket = null;
            return array;
        }
        return null;
    }

    @Override
    public byte[] getPlayerInfoPacket(long timeDiff)
    {
        if (isPlayerInfoOk())
        {
            int bufferSize = header.length + playerInfo.length + CRC_BYTES.length;

//            int bufferSize = header.length + playerInfo.length + Objects.requireNonNull(statePosPacket).length + CRC_BYTES.length;
            ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);

            byteBuffer.put(header);
            byteBuffer.put(playerInfo);
            byteBuffer.put(CRC_BYTES);

            return byteBuffer.array();
        }

        return null;
    }

    @Override
    public boolean isCarStateOk()
    {
        return carState != null;
    }

    @Override
    public byte[] getCarStatePacket(long timeDiff)
    {
        if (isCarStateOk())
        {
            byte[] statePosPacket = getStatePosPacket(timeDiff);
            int bufferSize = header.length + Objects.requireNonNull(statePosPacket).length + CRC_BYTES.length;
            ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
            byteBuffer.put(header);
            byteBuffer.put(carState);
            byteBuffer.put(CRC_BYTES);
            byte[] array = byteBuffer.array();
            statePosPacket = null;
            return array;
        }
        return null;
    }

    private byte[] getStatePosPacket(long timeDiff)
    {
        if (isCarStateOk())
        {
            byte[] clone = carState.clone();
            byte[] timeDiffBytes = ByteBuffer.allocate(2).putShort((short) timeDiff).array();
            clone[2] = timeDiffBytes[0];
            clone[3] = timeDiffBytes[1];
            int bufferSize = clone.length;
            ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
            byteBuffer.put(clone);
            byte[] array = byteBuffer.array();
            clone = null;
            timeDiffBytes = null;
            return array;
        }
        return null;
    }
}
