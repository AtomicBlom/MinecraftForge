package net.minecraftforge.common.util;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;
import java.util.Map;

/**
 * Created by rainwarrior on 5/31/16.
 */
public class DisjointSet<A>
{
    private final Map<A, Pair<Optional<A>, Integer>> data = Maps.newHashMap();

    public boolean makeSet(A a)
    {
        if(!data.containsKey(a))
        {
            data.put(a, Pair.of(Optional.<A>absent(), 0));
            return true;
        }
        return false;
    }

    public Optional<A> find(A a)
    {
        if(data.containsKey(a))
        {
            Pair<Optional<A>, Integer> pair = data.get(a);
            if(pair.getLeft() == Optional.absent())
            {
                return Optional.of(a);
            }
            A root = find(pair.getKey().get()).get();
            data.put(a, Pair.of(Optional.of(root), pair.getRight()));
            return Optional.of(root);
        }
        return Optional.absent();
    }

    public void union(A a, A b)
    {
        Optional<A> aR = find(a), bR = find(b);
        if(aR.isPresent() && bR.isPresent())
        {
            A aRoot = aR.get(), bRoot = bR.get();
            Pair<Optional<A>, Integer> aData = data.get(aRoot);
            Pair<Optional<A>, Integer> bData = data.get(bRoot);
            int aRank = aData.getRight();
            int bRank = bData.getRight();
            if (aRank < bRank)
            {
                data.put(aRoot, Pair.of(Optional.of(bRoot), aRank));
            }
            else if (bRank < aRank)
            {
                data.put(bRoot, Pair.of(Optional.of(aRoot), bRank));
            }
            else
            {
                data.put(aRoot, Pair.of(Optional.of(bRoot), aRank));
                data.put(bRoot, Pair.of(bData.getLeft(), bRank + 1));
            }
        }
        else
        {
            throw new IllegalStateException("one of the arguments isn't in the set: " + a + " or " + b);
        }
    }

    public boolean isSame(A a, A b)
    {
        Optional<A> aR = find(a), bR = find(b);
        if(aR.isPresent() && bR.isPresent())
        {
            return Objects.equal(aR.get(), bR.get());
        }
        return false;
    }

    public ImmutableMap<A, ImmutableSet<A>> toMap()
    {
        ImmutableMultimap.Builder<A, A> rootBuilder = ImmutableMultimap.builder();
        for (Map.Entry<A, Pair<Optional<A>, Integer>> entry : data.entrySet())
        {
            rootBuilder.put(find(entry.getKey()).get(), entry.getKey());
        }
        ImmutableMultimap<A, A> rootMap = rootBuilder.build();
        ImmutableMap.Builder<A, ImmutableSet<A>> builder = ImmutableMap.builder();
        for(Collection<A> as: rootMap.asMap().values())
        {
            ImmutableSet<A> set = ImmutableSet.copyOf(as);
            for(A a : set)
            {
                builder.put(a, set);
            }
        }
        return builder.build();
    }
}
