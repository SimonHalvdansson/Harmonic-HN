"""Trace the production PNG alpha into compact cubic Beziers (Pillow + NumPy).

The PNG is read only. Coordinates retain its 1024 x 1024 adaptive-icon canvas.
Run from any directory; outputs are beside this script.
"""
from pathlib import Path
import json
import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
SOURCE = ROOT / 'app/src/main/res/drawable-nodpi/ic_launcher_foreground_1024.png'


def contours(alpha):
    # Interpolate the 50% alpha contour, locating pixels at their centers.
    segments = []
    lookup = {1: [(3, 0)], 2: [(0, 1)], 3: [(3, 1)], 4: [(1, 2)],
              5: [(3, 0), (1, 2)], 6: [(0, 2)], 7: [(3, 2)],
              8: [(2, 3)], 9: [(2, 0)], 10: [(0, 1), (2, 3)],
              11: [(2, 1)], 12: [(1, 3)], 13: [(1, 0)], 14: [(0, 3)]}
    inside = alpha >= 127.5
    masks = (inside[:-1, :-1].astype(int) + inside[:-1, 1:] * 2
             + inside[1:, 1:] * 4 + inside[1:, :-1] * 8)
    for y, x in np.argwhere((masks > 0) & (masks < 15)):
        corners = [(x+.5, y+.5), (x+1.5, y+.5), (x+1.5, y+1.5), (x+.5, y+1.5)]
        values = [alpha[y,x], alpha[y,x+1], alpha[y+1,x+1], alpha[y+1,x]]
        def edge(i):
            j = (i+1) % 4
            u = (127.5-values[i]) / (values[j]-values[i])
            return tuple(round(corners[i][k] + u*(corners[j][k]-corners[i][k]), 5) for k in (0,1))
        for a, b in lookup[masks[y,x]]:
            segments.append((edge(a), edge(b)))
    neighbors = {}
    for a,b in segments:
        neighbors.setdefault(a, []).append(b)
        neighbors.setdefault(b, []).append(a)
    loops = []
    while neighbors:
        start = min(neighbors)
        loop, previous, current = [start], None, start
        while True:
            following = next(p for p in neighbors[current] if p != previous)
            previous, current = current, following
            if current == start:
                break
            loop.append(current)
        for p in loop:
            del neighbors[p]
        if len(loop) > 20:
            loops.append(np.array(loop))
    return sorted(loops, key=len, reverse=True)


def unit(v):
    return v / max(np.linalg.norm(v), 1e-12)


def fit(points, left, right, tolerance=.6):
    if len(points) == 2:
        dist = np.linalg.norm(points[1]-points[0])/3
        return [[points[0], points[0]+left*dist, points[1]+right*dist, points[1]]]
    distances = np.linalg.norm(np.diff(points, axis=0), axis=1)
    u = np.r_[0, np.cumsum(distances)] / distances.sum()
    b = np.array([(1-u)**3, 3*u*(1-u)**2, 3*u*u*(1-u), u**3]).T
    a = np.stack([b[:,1,None]*left, b[:,2,None]*right], axis=2).reshape(-1,2)
    residual = (points - (b[:,0]+b[:,1])[:,None]*points[0]
                - (b[:,2]+b[:,3])[:,None]*points[-1])
    lengths = np.linalg.lstsq(a, residual.reshape(-1), rcond=None)[0]
    if min(lengths) < 1e-5:
        lengths[:] = np.linalg.norm(points[-1]-points[0])/3
    curve = np.array([points[0], points[0]+left*lengths[0], points[-1]+right*lengths[1], points[-1]])
    error = np.linalg.norm(b@curve-points, axis=1)
    split = int(np.argmax(error))
    if error[split] <= tolerance:
        return [curve]
    split = max(1, min(len(points)-2, split))
    tangent = unit(points[split-1]-points[split+1])
    return fit(points[:split+1], left, tangent, tolerance) + fit(points[split:], -tangent, right, tolerance)


def main():
    im = Image.open(SOURCE).convert('RGBA')
    loops = contours(np.array(im.getchannel('A'), dtype=float))
    result = []
    for points in loops:
        # Suppress subpixel raster stair steps without changing the silhouette.
        weights = [1, 2, 3, 4, 3, 2, 1]
        points = sum(np.roll(points, i-3, axis=0)*w for i,w in enumerate(weights))/16
        # Split closed curves at opposite contour positions, preserving tangency.
        half = len(points)//2
        tangent0 = unit(points[1]-points[-1])
        tangent1 = unit(points[half+1]-points[half-1])
        curves = fit(points[:half+1], tangent0, -tangent1)
        curves += fit(np.vstack([points[half:], points[0]]), tangent1, -tangent0)
        result.append({'start': np.round(curves[0][0], 3).tolist(),
                       'curves': [np.round(np.array(c)[1:], 3).reshape(-1).tolist() for c in curves]})
    data = {'source': str(SOURCE.relative_to(ROOT)).replace('\\','/'),
            'viewport': 1024, 'ink': '#341000', 'background': '#FFDBC9',
            'contours': result}
    (HERE/'icon-geometry.json').write_text(json.dumps(data, separators=(',',':'))+'\n')
    print(f'Traced {len(loops)} contours into {sum(len(p["curves"]) for p in result)} cubic segments.')


if __name__ == '__main__':
    main()
