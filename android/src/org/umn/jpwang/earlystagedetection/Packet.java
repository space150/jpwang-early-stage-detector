package org.umn.jpwang.earlystagedetection;

public class Packet
{
    public enum Type
    {
        Config,
        Start
    };

    public static final byte SOF = (byte)0xFE;

    private byte _command;
    private byte[] _payload;
    private byte _payloadLength;
    private byte _xor;
    private byte[] _buffer;
    public byte[] getBuffer() { return _buffer; };

    public Packet(Type type)
    {
        if ( type == Type.Config )
        {
            _command = (byte)0x04;
            _payload = generateConfigPayload();
        }
        else if ( type == Type.Start )
        {
            _command = (byte)0x00;
            _payload = generateStartPayload();
        }

        _payloadLength = (byte)_payload.length;

        computeXor();

        computeBuffer();
    }

    private void computeXor()
    {
        _xor = (byte)0x00;
        _xor ^= _command;
        _xor ^= _payloadLength;

        for ( int i = 0; i < _payloadLength; i++ )
            _xor ^= _payload[i];
    }

    private void computeBuffer()
    {
        _buffer = new byte[4 + _payloadLength];
        _buffer[0] = SOF;
        _buffer[1] = _command;
        _buffer[2] = _payloadLength;
        System.arraycopy(_payload, 0, _buffer, 3, _payloadLength);
        _buffer[3 + _payloadLength] = _xor;
    }

    private byte[] generateConfigPayload()
    {
        byte[] p = new byte[29];
        p[0] = (byte)46;
        p[1] = (byte)47;
        p[2] = (byte)45;
        p[3] = (byte)16;
        p[4] = (byte)44;
        p[5] = (byte)17;
        p[6] = (byte)43;
        p[7] = (byte)18;
        p[8] = (byte)42;
        p[9] = (byte)19;
        p[10] = (byte)41;
        p[11] = (byte)20;
        p[12] = (byte)40;
        p[13] = (byte)21;
        p[14] = (byte)39;
        p[15] = (byte)38;
        p[16] = (byte)22;
        p[17] = (byte)37;
        p[18] = (byte)23;
        p[19] = (byte)36;
        p[20] = (byte)24;
        p[21] = (byte)35;
        p[22] = (byte)25;
        p[23] = (byte)34;
        p[24] = (byte)26;
        p[25] = (byte)33;
        p[26] = (byte)27;
        p[27] = (byte)32;
        p[28] = (byte)28;
        return p;
    }

    private byte[] generateStartPayload()
    {
        byte[] p = new byte[21];
        p[0] = (byte)154;
        p[1] = (byte)153;
        p[2] = (byte)153;
        p[3] = (byte)62;
        p[4] = (byte)0;
        p[5] = (byte)0;
        p[6] = (byte)122;
        p[7] = (byte)68;
        p[8] = (byte)205;
        p[9] = (byte)204;
        p[10] = (byte)76;
        p[11] = (byte)62;
        p[12] = (byte)0;
        p[13] = (byte)0;
        p[14] = (byte)72;
        p[15] = (byte)66;
        p[16] = (byte)0;
        p[17] = (byte)0;
        p[18] = (byte)128;
        p[19] = (byte)63;
        p[20] = (byte)4;
        return p;
    }

}
