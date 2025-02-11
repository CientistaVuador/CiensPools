//P is rayOrigin + (rayDirection * t)
//where t is the return value
//returns -1.0 if the ray is outside the box
float intersectRayInsideBox(vec3 rayOrigin, vec3 rayDirection, mat4 worldToLocal) {
    vec3 localOrigin = (worldToLocal * vec4(rayOrigin, 1.0)).xyz;
    vec3 aabbCheck = abs(localOrigin);
    if (max(aabbCheck.x, max(aabbCheck.y, aabbCheck.z)) > 1.0) {
        return -1.0;
    }
    vec3 localDirection = mat3(worldToLocal) * rayDirection;
    vec3 firstPlane = (vec3(-1.0) - localOrigin) / localDirection;
    vec3 secondPlane = (vec3(1.0) - localOrigin) / localDirection;
    vec3 furthestPlane = max(firstPlane, secondPlane);
    return min(furthestPlane.x, min(furthestPlane.y, furthestPlane.z));
}
