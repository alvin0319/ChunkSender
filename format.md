# Basic Structure

unsigned varint - ResponseType enum

# Login
unsigned varint - LoginCode enum

# Chunk
```
int - chunkX
int - chunkZ
unsigned varint - dimension byte length
ByteArray - dimension
unsigned varint - worldName byte length
ByteArray - worldName
```

# Structure
```
int - chunkX
int - chunkZ
unsigned varint - dimension byte length
ByteArray - dimension
unsigned varint - worldName byte length
ByteArray - worldName
unsigned varint - structureType byte length
ByteArray - structureType
counter - int
```

# Biome
```
int - chunkX
int - chunkZ
unsigned varint - dimension byte length
ByteArray - dimension
unsigned varint - worldName byte length
ByteArray - worldName
unsigned varint - biomeType byte length
ByteArray - biomeType
counter - int
```

# ChunkResponse
```
int - chunkX
int - chunkZ
unsigned varint - dimension byte length
ByteArray - dimension
unsigned varint - worldName byte length
ByteArray - worldName
unsigned varint - chunk data length
ByteArray - chunk data
```

# StructureResponse
```
int - chunkX
int - chunkZ
success - byte
if success is 1:
    unsigned varint - dimension byte length
    ByteArray - dimension
    unsigned varint - worldName byte length
    ByteArray - worldName
    resultX - int
    resultY - int
    resultZ - int
    unsigned varint - structureType byte length
    ByteArray - structureType
counter - int
```

# BiomeResponse
```
int - chunkX
int - chunkZ
success - byte
if success is 1:
    unsigned varint - dimension byte length
    ByteArray - dimension
    unsigned varint - worldName byte length
    ByteArray - worldName
    resultX - int
    resultZ - int
    unsigned varint - biomeType byte length
    ByteArray - biomeType
counter - int
```

# Enum
```java
enum ResponseType {
    CHUNK,
    COORDINATE,
    LOGIN
}

enum LoginCode {
    SUCCESS,
    UNAUTHORIZED
}

```