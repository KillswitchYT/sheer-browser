#!/usr/bin/env python3
"""Turns the output of the `bsdiff` tool (bzip2 inside) into Sheer's own patch file (deflate inside), which the app can read with
nothing but the Java standard library.   usage: mkpatch.py <bsdiff-file> <out-file>
Layout of the result:  "SHRP1", newSize (int64 LE), then three blocks (control, diff, extra), each: rawLength int64 LE,
compressedLength int64 LE, deflate data. The control block is a list of int64 LE triples (add, copy, seek) exactly as in bsdiff."""
import bz2, struct, sys, zlib

def offtin(b):  # bsdiff stores numbers as sign and magnitude, little endian
    y = struct.unpack('<Q', b)[0]
    v = y & 0x7FFFFFFFFFFFFFFF
    return -v if (y >> 63) else v

data = open(sys.argv[1], 'rb').read()
if data[:8] != b'BSDIFF40':
    sys.exit('not a bsdiff patch')
clen, dlen, newsize = offtin(data[8:16]), offtin(data[16:24]), offtin(data[24:32])
ctrl = bz2.decompress(data[32:32 + clen])
diff = bz2.decompress(data[32 + clen:32 + clen + dlen])
extra = bz2.decompress(data[32 + clen + dlen:]) if len(data) > 32 + clen + dlen else b''
ctrl2 = b''.join(struct.pack('<q', offtin(ctrl[i:i + 8])) for i in range(0, len(ctrl), 8))
out = bytearray(b'SHRP1' + struct.pack('<q', newsize))
for blk in (ctrl2, diff, extra):
    z = zlib.compress(blk, 9)
    out += struct.pack('<qq', len(blk), len(z)) + z
open(sys.argv[2], 'wb').write(out)
print('patch %d bytes for a %d byte file' % (len(out), newsize))
