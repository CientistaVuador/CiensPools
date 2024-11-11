layout (location = VAO_INDEX_POSITION_XYZ) in vec3 vertexPosition;
layout (location = VAO_INDEX_TEXTURE_XY) in vec2 vertexTexture;
layout (location = VAO_INDEX_LIGHTMAP_TEXTURE_XY) in vec2 vertexLightmapTexture;
layout (location = VAO_INDEX_NORMAL_XYZ) in vec3 vertexNormal;
layout (location = VAO_INDEX_TANGENT_XYZ) in vec3 vertexTangent;
layout (location = VAO_INDEX_BONE_IDS_XYZW) in ivec4 vertexBoneIds;
layout (location = VAO_INDEX_BONE_WEIGHTS_XYZW) in vec4 vertexBoneWeights;

uniform mat4 projection;
uniform mat4 view;
uniform mat4 model;
uniform mat3 normalModel;

uniform mat4 boneMatrices[MAX_AMOUNT_OF_BONES + 1];

out VertexData {
    vec3 worldPosition;
    vec2 worldTexture;
    vec2 worldLightmapTexture;
    vec3 worldNormal;

    mat3 TBN;
    vec3 tangentPosition;
} outVertex;

void main() {
    vec3 localTangent = vec3(0.0);
    vec3 localNormal = vec3(0.0);
    vec4 localPosition = vec4(0.0);

    for (int i = 0; i < MAX_AMOUNT_OF_BONE_WEIGHTS; i++) {
        int boneId = vertexBoneIds[i] + 1;
        float weight = vertexBoneWeights[i];

        mat4 boneModel = boneMatrices[boneId];
        mat3 normalBoneModel = mat3(boneModel);

        localTangent += normalBoneModel * vertexTangent * weight;
        localNormal += normalBoneModel * vertexNormal * weight;
        localPosition += boneModel * vec4(vertexPosition, 1.0) * weight;
    }

    vec3 tangent = normalize(normalModel * localTangent);
    vec3 normal = normalize(normalModel * localNormal);
    vec4 worldPosition = model * localPosition;

    outVertex.worldPosition = worldPosition.xyz;
    outVertex.worldTexture = vertexTexture;
    outVertex.worldLightmapTexture = vertexLightmapTexture;
    outVertex.worldNormal = normal;

    outVertex.TBN = mat3(tangent, cross(normal, tangent), normal);
    outVertex.tangentPosition = transpose(outVertex.TBN) * outVertex.worldPosition;

    gl_Position = projection * view * worldPosition;
}
